# Releasing BANKSHOT // ZERO

## Validate the release

```bash
./scripts/build.sh
./scripts/serve.sh
```

In a desktop browser, start a run, move, aim and shoot, bounce a shot off a wall, dash, pause/resume, toggle sound, choose an upgrade, and restart after a run. Check the browser console for errors. Resize the window and check fullscreen. The release archive is `dist/bankshot-zero-web.zip`; its root must contain `index.html` and `game.js`.

The distribution contains all runtime assets. Use relative asset paths so the same build works under both a GitHub Pages repository path and itch.io's embed path.

## First GitHub publication

Run these commands from the project directory after `gh auth status` confirms the intended account. The repository name can be changed in the first command.

```bash
git init -b main
git add .
git commit -m "Ship BANKSHOT // ZERO"
gh repo create bankshot-zero --public --source=. --remote=origin --push
gh api --method POST 'repos/{owner}/{repo}/pages' -f build_type=workflow
gh workflow run pages.yml
```

`{owner}` and `{repo}` are expanded by GitHub CLI from the current repository. If Pages already exists, use `--method PUT` for its configuration instead. In the GitHub repository's **Settings → Pages**, the source should read **GitHub Actions**.

## Versioned GitHub release

Update `pom.xml` and `docs/release-notes.md` for the version, commit the changes, then:

```bash
git tag -a v1.0.0 -m "BANKSHOT // ZERO 1.0.0"
git push origin main v1.0.0
gh run list --workflow build.yml --limit 5
```

The tag workflow tests and builds the game and attaches `bankshot-zero-web.zip` to its GitHub Release. To create the release from an already validated local build instead:

```bash
gh release create v1.0.0 dist/bankshot-zero-web.zip --verify-tag --title "BANKSHOT // ZERO v1.0.0" --notes-file docs/release-notes.md
```

If the automated workflow has already created that release, update the asset with `gh release upload v1.0.0 dist/bankshot-zero-web.zip --clobber`.

## First itch.io publication

1. Sign in at [itch.io](https://itch.io/) and [create a project](https://itch.io/game/new) with the slug `bankshot-zero`.
2. Use the title **BANKSHOT // ZERO**, Kind of project **HTML**, classification **Games**, pricing **No payments**, and the description in [ITCH_PAGE.md](ITCH_PAGE.md).
3. Upload `dist/bankshot-zero-web.zip`. Check **This file will be played in the browser**.
4. Use **Click to launch in fullscreen**, or embed at **1280 × 800** with the fullscreen button enabled. Keep **Mobile friendly** unchecked: the game uses a keyboard and mouse.
5. Save as a draft, play the actual embedded upload, then set visibility to **Public** and save.

The browser archive is the shipped game. The Maven JAR contains compiled Java classes for development; it is not a desktop game launcher.

## Update itch.io with Butler

Install the official [Butler CLI](https://itch.io/docs/butler/), sign in once with `butler login`, and substitute the itch.io account name:

```bash
./scripts/build.sh
ITCH_USER=your-itch-username
butler push dist/bankshot-zero-web.zip "$ITCH_USER/bankshot-zero:html5" --userversion 1.0.0
```

After the first Butler push, edit the itch.io project and mark that channel as **HTML5 / Playable in browser**. Butler's channel name does not set that flag automatically. Keep the project's Kind of game set to **HTML**. Later pushes to the same channel update the existing upload.

References: [TeaVM Maven](https://teavm.org/docs/tooling/maven.html), [GitHub Pages custom workflows](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages), [itch.io HTML5 uploads](https://itch.io/docs/creators/html5), [Butler pushing builds](https://itch.io/docs/butler/pushing.html).
