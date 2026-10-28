#!/usr/bin/env sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
URL=$(sed -n 's/^distributionUrl=//p' "$APP_HOME/gradle/wrapper/gradle-wrapper.properties" | sed 's/\\:/:/g')
NAME=$(basename "$URL" .zip)
FOLDER=${NAME%-bin}
GRADLE_HOME_DIR=${GRADLE_USER_HOME:-"$HOME/.gradle"}
DEST="$GRADLE_HOME_DIR/wrapper/dists/$NAME-v5"
BIN="$DEST/$FOLDER/bin/gradle"
if [ ! -x "$BIN" ]; then
    mkdir -p "$DEST"
    ARCHIVE="$DEST/$NAME.zip"
    if command -v curl >/dev/null 2>&1; then
        curl -fL "$URL" -o "$ARCHIVE"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$ARCHIVE" "$URL"
    else
        echo "curl or wget is required to download Gradle" >&2
        exit 1
    fi
    unzip -q -o "$ARCHIVE" -d "$DEST"
fi
exec "$BIN" -p "$APP_HOME" "$@"
