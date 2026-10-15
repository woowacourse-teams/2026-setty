"""Replay the historical JPA query comparison on its fixed source revision."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile


REVISION = '9938ea86fa9de7c428caec4eb435f9d6ab184d24'


def main():
    tool = Path(__file__).resolve().parent
    server = tool.parents[1]
    repo = server.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, help='New output directory; existing paths are refused')
    args = parser.parse_args()
    # Check the historical revision before allocating a new run. Shallow clones may need a fetch.
    subprocess.run(['git', 'cat-file', '-e', REVISION + '^{commit}'], cwd=repo, check=True)
    if args.output:
        output = args.output.resolve()
        output.mkdir(parents=True, exist_ok=False)
    else:
        reports = server / 'build/reports'
        reports.mkdir(parents=True, exist_ok=True)
        output = Path(tempfile.mkdtemp(prefix='thumbnail-replay-', dir=reports))
    source = output / 'source'
    source.mkdir()
    archive = output / 'source.tar'
    subprocess.run(['git', 'archive', '--format=tar', f'--output={archive}', REVISION, 'server'],
                   cwd=repo, check=True)
    subprocess.run(['tar', '-xf', str(archive), '-C', str(source)], check=True)
    archive.unlink()
    (output / 'source-commit.txt').write_text(REVISION + '\n')
    harness = [tool / 'benchmark.gradle', tool / 'analyze.py', tool / 'reproduce.py',
               tool / 'src/test/java/setty/performance/ThumbnailQueryComparisonTest.java']
    (output / 'harness-sha256.json').write_text(json.dumps({
        str(path.relative_to(tool)): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in harness}, indent=2) + '\n')
    print(f'Historical source: {REVISION}\nOutput: {output}', flush=True)
    commands = [
        ('regression.log', ['./gradlew', 'test', '--tests',
                            'setty.platform.listing.repository.ListingImageRepositoryTest',
                            '--tests', 'setty.platform.listing.ListingApiTest',
                            '--tests', 'setty.global.logging.ListingTimingHttpIntegrationTest']),
        ('run.log', ['./gradlew', 'test', '-I', str(tool / 'benchmark.gradle'), '--tests',
                     'setty.performance.ThumbnailQueryComparisonTest',
                     f'-Dthumbnail.benchmark.output={output}', '--rerun-tasks']),
    ]
    for logfile, command in commands:
        print(f'Running {logfile}; details are in the output directory', flush=True)
        with (output / logfile).open('w') as log:
            subprocess.run(command, cwd=source / 'server', stdout=log, stderr=subprocess.STDOUT, check=True)
    with (output / 'analysis.log').open('w') as log:
        subprocess.run(['python3', str(tool / 'analyze.py'), str(output)],
                       stdout=log, stderr=subprocess.STDOUT, check=True)
    print(f'Completed and verified. Read {output / "comparison.json"}', flush=True)


if __name__ == '__main__':
    main()
