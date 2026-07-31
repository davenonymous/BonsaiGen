#!/usr/bin/env bash
# Writes a README.md gallery into every pack directory of a previews folder,
# plus an index README.md at the root. GitHub renders these when browsing the
# preview branch, so each mod gets a free tree gallery. Images are referenced
# relatively; animated GIF is preferred, WebP is the fallback.
#
#   scripts/generate-galleries.sh <previews-dir>
set -euo pipefail

PREVIEWS_DIR="${1:?usage: generate-galleries.sh <previews-dir>}"
[ -d "$PREVIEWS_DIR" ] || { echo "error: not a directory: $PREVIEWS_DIR" >&2; exit 1; }

pack_count=0
declare -A pack_trees

for pack_dir in "$PREVIEWS_DIR"/*/; do
    pack="$(basename "$pack_dir")"

    # One entry per model stem; prefer .gif over .webp, ignore frame dumps.
    declare -A images=()
    while IFS= read -r file; do
        stem="${file%.*}"
        case "$file" in
            *.frame*.png) continue ;;
            *.gif) images["$stem"]="$file" ;;
            *.webp) [ -z "${images[$stem]:-}" ] && images["$stem"]="$file" ;;
        esac
    done < <(cd "$pack_dir" && find . \( -name '*.gif' -o -name '*.webp' \) -type f | sed 's|^\./||' | sort)

    [ "${#images[@]}" -gt 0 ] || { unset images; continue; }

    mapfile -t stems < <(printf '%s\n' "${!images[@]}" | sort)
    {
        echo "# $pack"
        echo
        echo "${#stems[@]} bonsai tree preview(s), rendered by \`create-tree-gif\`."
        echo
        for stem in "${stems[@]}"; do
            echo "### $stem"
            echo
            echo "<img src=\"${images[$stem]}\" width=\"256\" alt=\"$stem\">"
            echo
        done
    } > "$pack_dir/README.md"

    pack_trees["$pack"]="${#stems[@]}"
    pack_count=$((pack_count + 1))
    unset images
done

{
    echo "# Bonsai tree previews"
    echo
    echo "Rendered previews of the multiblock tree models, grouped by mod."
    echo "Browse a folder to see its gallery."
    echo
    for pack in $(printf '%s\n' "${!pack_trees[@]}" | sort); do
        echo "- [$pack]($pack/) (${pack_trees[$pack]} tree(s))"
    done
} > "$PREVIEWS_DIR/README.md"

echo "generated $pack_count pack galleries in $PREVIEWS_DIR"
