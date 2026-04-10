import os
from pathlib import Path

OUTPUT_FILENAME = "code_dump.txt"

EXCLUDED_DIRS = {
    ".git", ".idea", ".vscode", "node_modules", "target",
    "__pycache__", ".mvn"
}

EXCLUDED_EXTENSIONS = {
    ".class", ".jar", ".exe", ".dll", ".png", ".jpg", ".jpeg",
    ".gif", ".bmp", ".ico", ".pdf", ".zip", ".tar", ".gz"
}


def find_project_root(start_path: Path) -> Path:
    """
    Remonte les dossiers jusqu'à trouver la racine du projet Maven
    (présence d'un pom.xml). Si non trouvé, utilise le dossier courant.
    """
    current = start_path.resolve()

    if current.is_file():
        current = current.parent

    while True:
        if (current / "pom.xml").exists():
            return current

        if current.parent == current:
            return start_path.resolve()

        current = current.parent


def is_valid_file(file_path: Path) -> bool:
    return file_path.suffix.lower() not in EXCLUDED_EXTENSIONS


def should_skip_dir(dir_name: str) -> bool:
    return dir_name in EXCLUDED_DIRS


def write_file_content(output_file, file_path: Path, root_dir: Path) -> None:
    rel_path = file_path.relative_to(root_dir)

    try:
        content = file_path.read_text(encoding="utf-8", errors="ignore")
    except Exception as e:
        content = f"[ERROR reading file: {e}]"

    output_file.write(f"{rel_path.as_posix()}\n")
    output_file.write(content)
    output_file.write("\n\n" + "=" * 80 + "\n\n")


def process_directory(current_dir: Path, root_dir: Path) -> None:
    """
    Crée un code_dump.txt dans current_dir contenant le code de tous les
    fichiers du dossier courant et de ses sous-dossiers.
    """
    output_path = current_dir / OUTPUT_FILENAME

    with output_path.open("w", encoding="utf-8") as out_file:
        for root, dirs, files in os.walk(current_dir):
            dirs[:] = [d for d in dirs if not should_skip_dir(d)]

            for file_name in files:
                if file_name == OUTPUT_FILENAME:
                    continue

                file_path = Path(root) / file_name

                if file_path.is_file() and is_valid_file(file_path):
                    write_file_content(out_file, file_path, root_dir)


def generate_global_file(root_dir: Path) -> None:
    """
    Crée un code_dump.txt dans /src contenant tout le code du projet.
    """
    src_dir = root_dir / "src"
    src_dir.mkdir(parents=True, exist_ok=True)

    output_path = src_dir / OUTPUT_FILENAME

    with output_path.open("w", encoding="utf-8") as out_file:
        for root, dirs, files in os.walk(root_dir):
            dirs[:] = [d for d in dirs if not should_skip_dir(d)]

            for file_name in files:
                if file_name == OUTPUT_FILENAME:
                    continue

                file_path = Path(root) / file_name

                if file_path.is_file() and is_valid_file(file_path):
                    write_file_content(out_file, file_path, root_dir)


def main() -> None:
    script_path = Path(__file__).resolve()
    root_dir = find_project_root(script_path.parent)

    print(f"📁 Racine détectée : {root_dir}")
    print("🔍 Génération des fichiers...")

    generate_global_file(root_dir)

    for root, dirs, _ in os.walk(root_dir):
        dirs[:] = [d for d in dirs if not should_skip_dir(d)]
        process_directory(Path(root), root_dir)

    print("✅ Terminé !")


if __name__ == "__main__":
    main()