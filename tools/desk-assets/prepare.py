"""Turns the raw generated / downloaded images of the desk scene into the web assets the game loads.

    python -P tools/desk-assets/prepare.py            (needs Pillow and numpy; run from the repo root)

Reads  design/generated/desk/  and  design/refs/desk/wood_colors/
Writes frontend/src/assets/desk/

  room-pano.webp    the 360° room, turned so the man sits where the reference puts him
  desk-wood.webp    the desk top: brightness flattened and made to tile
  wood-*.webp       the board's woods (squares and frame), made to tile

Nothing here is needed at run time: the outputs are committed.
"""
import sys
import numpy as np
from PIL import Image, ImageFilter

RAW = 'design/generated/desk/'
WOODS_DIR = 'design/refs/desk/wood_colors/'
OUT = 'frontend/src/assets/desk/'

# Where the man of the panorama stands, in degrees from straight ahead (negative: to the left). The reference
# has him left of the board's centre.
MAN_LON = -12.0
# He sits at this fraction of the raw panorama's width (measured on the file)
MAN_AT = 0.4135
# The whole room is lowered by this many degrees, so that the desk's far edge cuts the man at the chest and the
# game's camera (which looks down at the board) still sees him and the shelves behind
PITCH_DOWN = 10.0
# The room is lifted by this factor (the picture is darker than the reference's background), with the highlights kept
ROOM_GAIN = 1.5


def flatten(img: Image.Image, sigma: float) -> np.ndarray:
    """Divide out the slow brightness changes (a lit corner, a vignette), keeping the mean colour."""
    a = np.asarray(img.convert('RGB')).astype(np.float32)
    low = np.asarray(img.convert('RGB').filter(ImageFilter.GaussianBlur(sigma))).astype(np.float32)
    return np.clip(a / np.maximum(low, 1) * low.reshape(-1, 3).mean(0), 0, 255)


def make_tile(a: np.ndarray) -> np.ndarray:
    """Cross-fade the picture with itself shifted by half, one axis at a time: the shifted copy's own seam then
    sits where its weight is exactly zero. (Weighting both axes at once leaves seams along the edges.)"""
    h, w, _ = a.shape
    bump = lambda n: np.sin(np.pi * np.linspace(0, 1, n)) ** 2
    wx = bump(w)[None, :, None]
    a = a * wx + np.roll(a, w // 2, 1) * (1 - wx)
    wy = bump(h)[:, None, None]
    return a * wy + np.roll(a, h // 2, 0) * (1 - wy)


def sharpen(img: Image.Image) -> Image.Image:
    """The cross-fade softens the middle of the picture a little."""
    return img.filter(ImageFilter.UnsharpMask(radius=2.2, percent=70, threshold=2))


def save(img: Image.Image, name: str, quality: int = 88) -> None:
    img.save(OUT + name, 'WEBP', quality=quality, method=6)
    print(f'{name}: {img.size[0]}x{img.size[1]}')


def lower(a: np.ndarray, degrees: float) -> np.ndarray:
    """Turn the whole sphere about its sideways axis, so that what was `degrees` above the horizon appears on it.
    Done by finding, for every pixel of the result, the pixel of the source it comes from (bilinear)."""
    h, w, _ = a.shape
    lon = (np.arange(w) / w - 0.25) * 2 * np.pi          # 0 = straight ahead (column 0.25, see room())
    lat = (0.5 - (np.arange(h) + 0.5) / h) * np.pi
    lon, lat = np.meshgrid(lon, lat)
    x, y, z = np.cos(lat) * np.sin(lon), np.sin(lat), np.cos(lat) * np.cos(lon)
    c, s = np.cos(np.radians(degrees)), np.sin(np.radians(degrees))
    y, z = y * c + z * s, z * c - y * s                    # the source direction of each output pixel: from higher up
    src_lat = np.arcsin(np.clip(y, -1, 1))
    src_lon = np.arctan2(x, z)
    u = (src_lon / (2 * np.pi) + 0.25) % 1.0 * w - 0.5
    v = (0.5 - src_lat / np.pi) * h - 0.5
    u0 = np.floor(u).astype(int)
    v0 = np.clip(np.floor(v).astype(int), 0, h - 2)
    fu = (u - u0)[..., None]
    fv = np.clip(v - v0, 0, 1)[..., None]
    u0 %= w
    u1 = (u0 + 1) % w
    f = a.astype(np.float32)
    top = f[v0, u0] * (1 - fu) + f[v0, u1] * fu
    bot = f[v0 + 1, u0] * (1 - fu) + f[v0 + 1, u1] * fu
    return np.clip(top * (1 - fv) + bot * fv, 0, 255).astype(np.uint8)


def room() -> None:
    pano = Image.open(RAW + 'room-pano-raw.png').convert('RGB')
    w = pano.size[0]
    # a panorama wraps: turning it is a shift of its columns. Moving the man from MAN_AT to the wanted column.
    # three.js looks at column 0.25 of an equirectangular picture when the camera faces -z (straight ahead here).
    want = 0.25 + MAN_LON / 360.0
    shift = round((want - MAN_AT) * w)
    turned = np.roll(np.asarray(pano), shift, 1)
    if PITCH_DOWN:
        turned = lower(turned, PITCH_DOWN)
    f = turned.astype(np.float32) / 255
    f = 1 - (1 - np.minimum(f * ROOM_GAIN, 1.0)) ** 1.0  # plain gain, clipped
    lifted = np.where(f > 0.8, 0.8 + (f - 0.8) * 0.6, f)      # ease the top so the lamps do not burn out
    save(Image.fromarray((np.clip(lifted, 0, 1) * 255).astype(np.uint8)), 'room-pano.webp', 90)


def desk() -> None:
    img = Image.open(RAW + 'desk-wood-raw.jpg')
    tile = make_tile(flatten(img, 220))
    out = sharpen(Image.fromarray(np.clip(tile, 0, 255).astype(np.uint8)))
    save(out.resize((2048, 1152), Image.LANCZOS), 'desk-wood.webp')


# The board's woods, graded towards the colours of the reference picture (its mean colours: maple squares 239,177,106;
# walnut squares 116,47,17; the frame is much darker than any of the downloads). `rotate`: the grain runs along the files.
WOODS = [
    # file, result, rotate a quarter turn, per-channel gain
    ('Wood095_2K-JPG_Color.jpg', 'wood-maple.webp', True, (1.17, 1.13, 0.95)),
    ('rosewood_veneer1_diff_4k.jpg', 'wood-walnut.webp', False, (1.0, 1.0, 1.0)),
    ('dark_wood_diff_4k.jpg', 'wood-frame.webp', False, (0.62, 0.55, 0.5)),
]


def board_woods() -> None:
    for src, name, rotate, gain in WOODS:
        try:
            img = Image.open(WOODS_DIR + src).convert('RGB')
        except FileNotFoundError:
            print('skipped (missing):', src)
            continue
        if img.size[0] > 2048:
            img = img.resize((2048, 2048 * img.size[1] // img.size[0]), Image.LANCZOS)
        if rotate:
            img = img.rotate(90, expand=True)
        a = np.asarray(img).astype(np.float32) * np.array(gain, dtype=np.float32)
        save(Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)), name)


if __name__ == '__main__':
    which = sys.argv[1:] or ['room', 'desk', 'woods']
    if 'room' in which: room()
    if 'desk' in which: desk()
    if 'woods' in which: board_woods()
