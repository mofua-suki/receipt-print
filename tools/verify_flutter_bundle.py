"""Extract the release ZIP and stage the tracked Flutter example against it."""

import plistlib
import shutil
import subprocess
import sys
from pathlib import Path
from zipfile import ZipFile


MODULES = (
    "receipt-core",
    "receipt-command-escpos",
    "receipt-command-star",
    "receipt-receiptline",
    "receipt-engine",
)
EXAMPLE = Path("packages/receipt_print/example")


def require_file(path):
    if not path.is_file() or path.stat().st_size == 0:
        raise ValueError(f"Missing or empty bundle file: {path}")
    return path


def verify_package(package, version):
    for path in (
        "LICENSE",
        "CHANGELOG.md",
        "README.md",
        "README.ja.md",
        "example/README.md",
        "example/README.ja.md",
        "example/pubspec.yaml",
        "example/lib/main.dart",
        "example/test/widget_test.dart",
        "third_party/unicode/LICENSE.txt",
        "pubspec.yaml",
        "lib/receipt_print.dart",
        "android/build.gradle.kts",
        "ios/receipt_print.podspec",
        "ios/receipt_print/Package.swift",
        "ios/receipt_print/Sources/receipt_print/PrivacyInfo.xcprivacy",
        "ios/receipt_print/Sources/receipt_print/Resources/receiptline.js",
        "ios/receipt_print/Sources/receipt_print/Resources/ReceiptLine-LICENSE",
    ):
        require_file(package / path)
    marker = require_file(package / "engine-version.txt").read_text(encoding="utf-8")
    if marker.strip() != version:
        raise ValueError(f"Expected engine-version.txt to contain {version!r}")

    for module in MODULES:
        maven = package / "android/maven/receipt-kmp"
        metadata = maven / module / version / f"{module}-{version}"
        jvm = maven / f"{module}-jvm" / version / f"{module}-jvm-{version}"
        for file in (f"{metadata}.pom", f"{metadata}.module", f"{jvm}.pom", f"{jvm}.jar"):
            require_file(Path(file))

    framework = package / "ios/Frameworks/ReceiptPrint.xcframework"
    with require_file(framework / "Info.plist").open("rb") as manifest:
        libraries = plistlib.load(manifest)["AvailableLibraries"]
    slices = set()
    for library in libraries:
        if library["SupportedPlatform"] != "ios" or "arm64" not in library["SupportedArchitectures"]:
            continue
        directory = framework / library["LibraryIdentifier"] / library["LibraryPath"]
        for path in ("ReceiptPrint", "Headers/ReceiptPrint.h", "Modules/module.modulemap"):
            require_file(directory / path)
        slices.add(library.get("SupportedPlatformVariant", "device"))
    if not {"device", "simulator"} <= slices:
        raise ValueError("ReceiptPrint.xcframework must contain arm64 device and simulator slices")


def prepare_example(repo, archive, package):
    version = (repo / "packages/receipt_print/engine-version.txt").read_text(encoding="utf-8").strip()
    package.mkdir()
    with ZipFile(archive) as bundle:
        bundle.extractall(package)
    verify_package(package, version)

    # Preserve the example's existing path: ../ dependency and Xcode paths.
    tracked = subprocess.check_output(["git", "ls-files", "-z", "--", EXAMPLE.as_posix()], cwd=repo)
    for raw_path in tracked.split(b"\0"):
        if not raw_path:
            continue
        source = Path(raw_path.decode())
        target = package / "example" / source.relative_to(EXAMPLE)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(repo / source, target)
    require_file(package / "example/pubspec.yaml")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit("Usage: verify_flutter_bundle.py ARCHIVE EXTRACTED_PACKAGE")
    repo = Path(__file__).resolve().parent.parent
    try:
        prepare_example(repo, Path(sys.argv[1]), Path(sys.argv[2]))
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        sys.exit(f"Bundle verification failed: {error}")
