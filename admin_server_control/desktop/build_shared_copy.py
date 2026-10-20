"""Builds the copy of the desktop app given to other admins.

"allow one to share it as an application to other admins" - a Windows app
folder (no Python needed on their computer), zipped, made from this app
without the parts that belong only on the owner's computer:
core/server_manager.py (it starts this computer's servers and holds its
tunnel settings) and the two Servers windows that use it. The copy starts
at the server address given here (written into app/core/default_server.txt);
each admin can still change it on the sign-in screen, so moving the servers
to a hosting provider does not need a new copy.

Run with the desktop app's own Python (PyInstaller is installed in venv):

    venv\\Scripts\\python.exe build_shared_copy.py https://<public address>/admin-api

The zip lands in dist\\.
"""
import argparse
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
NAME = "Dateroem Admin Control"
# Owner's computer only - never in the shared copy.
OWNER_ONLY = [
    "app/core/server_manager.py",
    "app/ui/server_control_window.py",
    "app/ui/widgets/server_control_panel.py",
]
OWNER_ONLY_MODULES = [path[:-3].replace("/", ".") for path in OWNER_ONLY]


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("server", help="the address the copy starts with, e.g. https://admin.example.com")
    args = parser.parse_args()

    sys.path.insert(0, str(HERE))
    from app.core import server_address
    from app.core.version import APP_VERSION

    problem = server_address.problem(args.server)
    if problem:
        sys.exit(problem)

    # PyInstaller works on a local copy: it is slow and fragile on the
    # \\wsl.localhost share this project lives on.
    build = Path(tempfile.gettempdir()) / "dateroem_desktop_build"
    shutil.rmtree(build, ignore_errors=True)
    src = build / "src"
    shutil.copytree(HERE / "app", src / "app", ignore=shutil.ignore_patterns("__pycache__", "*.pyc", "default_server.txt"))
    shutil.copy2(HERE / "launch.pyw", src / "launch.pyw")
    for path in OWNER_ONLY:
        (src / path).unlink()
    (src / "app/core/default_server.txt").write_text(server_address._normalized(args.server) + "\n", encoding="utf-8")

    command = [
        sys.executable, "-m", "PyInstaller", "--noconfirm", "--clean", "--windowed", "--onedir",
        "--name", NAME, "--icon", str(src / "app/assets/icon.ico"),
        "--add-data", f"{src / 'app/assets'};app/assets",
        "--add-data", f"{src / 'app/ui/assets'};app/ui/assets",
        "--add-data", f"{src / 'app/core/default_server.txt'};app/core",
        "--distpath", str(build / "dist"), "--workpath", str(build / "work"), "--specpath", str(build),
    ]
    for module in OWNER_ONLY_MODULES:
        command += ["--exclude-module", module]
    subprocess.run(command + [str(src / "launch.pyw")], check=True, cwd=src)

    # Nothing owner-only may have slipped into the program.
    bundled = (build / "work" / NAME / "PYZ-00.toc").read_text(encoding="utf-8", errors="replace")
    slipped = [module for module in OWNER_ONLY_MODULES if f"'{module}'" in bundled]
    if slipped:
        sys.exit(f"owner-only code got into the build: {slipped}")

    out = HERE / "dist"
    out.mkdir(exist_ok=True)
    archive = out / f"{NAME} {APP_VERSION}.zip"
    folder = build / "dist" / NAME
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as zipped:
        for file in sorted(folder.rglob("*")):
            if file.is_file():
                zipped.write(file, Path(NAME) / file.relative_to(folder))
    print(f"built {archive} ({archive.stat().st_size // (1024 * 1024)} MB), starting at {server_address._normalized(args.server)}")
    print(f"app folder for testing: {folder}")


if __name__ == "__main__":
    main()
