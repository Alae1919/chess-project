// Injected into every page the recorder opens: a visible mouse cursor with click
// ripples (headless browsers draw none) and a caption bar, styled like the app.
(() => {
  if (window.__demo) return;

  const CSS = `
    #demo-cursor {
      position: fixed; left: 0; top: 0; z-index: 2147483647; pointer-events: none;
      transform: translate(-200px, -200px); will-change: transform;
    }
    #demo-cursor svg { display: block; filter: drop-shadow(0 2px 4px rgba(0, 0, 0, .7)); }
    .demo-ripple {
      position: fixed; z-index: 2147483646; pointer-events: none;
      width: 40px; height: 40px; margin: -20px 0 0 -20px; border-radius: 50%;
      border: 2px solid rgba(243, 221, 162, .95); background: rgba(230, 194, 122, .18);
      animation: demo-ripple .55s ease-out forwards;
    }
    @keyframes demo-ripple { from { transform: scale(.25); opacity: 1; } to { transform: scale(1.5); opacity: 0; } }
    #demo-caption {
      position: fixed; left: 50%; bottom: 34px; z-index: 2147483645; pointer-events: none;
      transform: translate(-50%, 14px); opacity: 0; transition: opacity .45s ease, transform .45s ease;
      max-width: min(86vw, 980px); padding: 13px 28px 14px; text-align: center;
      background: rgba(11, 9, 7, .86); border: 1px solid rgba(230, 194, 122, .5); border-radius: 14px;
      box-shadow: 0 14px 44px rgba(0, 0, 0, .55); backdrop-filter: blur(8px);
    }
    #demo-caption.show { opacity: 1; transform: translate(-50%, 0); }
    #demo-caption .t { font: 500 23px/1.3 'Jost', 'Segoe UI', sans-serif; color: #efe6d6; }
    #demo-caption .s {
      margin-top: 5px; font: 500 13px/1.4 'Jost', 'Segoe UI', sans-serif; color: #e6c27a;
      letter-spacing: .14em; text-transform: uppercase;
    }
    #demo-caption .s:empty { display: none; }
  `;

  const ARROW = `<svg width="26" height="26" viewBox="0 0 24 24">
    <path d="M4 2.5 L4 19.5 L8.6 15.2 L11.6 22 L14.6 20.7 L11.7 14 L18 14 Z"
          fill="#fff" stroke="#111" stroke-width="1.4" stroke-linejoin="round"/></svg>`;

  let cursor, caption;

  function mount() {
    if (document.getElementById('demo-cursor')) return;
    const style = document.createElement('style');
    style.textContent = CSS;
    document.head.appendChild(style);

    cursor = document.createElement('div');
    cursor.id = 'demo-cursor';
    cursor.innerHTML = ARROW;
    document.body.appendChild(cursor);

    caption = document.createElement('div');
    caption.id = 'demo-caption';
    caption.innerHTML = '<div class="t"></div><div class="s"></div>';
    document.body.appendChild(caption);
  }

  const place = (x, y) => { if (cursor) cursor.style.transform = `translate(${x - 4}px, ${y - 2}px)`; };

  window.addEventListener('mousemove', (e) => place(e.clientX, e.clientY), true);
  window.addEventListener('pointermove', (e) => place(e.clientX, e.clientY), true);
  window.addEventListener('mousedown', (e) => {
    const r = document.createElement('div');
    r.className = 'demo-ripple';
    r.style.left = `${e.clientX}px`;
    r.style.top = `${e.clientY}px`;
    document.body.appendChild(r);
    setTimeout(() => r.remove(), 700);
  }, true);

  window.__demo = {
    caption(title, sub = '') {
      mount();
      caption.querySelector('.t').textContent = title;
      caption.querySelector('.s').textContent = sub;
      caption.classList.add('show');
    },
    hideCaption() { caption?.classList.remove('show'); },
    place,
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mount);
  else mount();
})();
