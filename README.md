[![Release](https://jitpack.io/v/umjammer/vavi-idea-plugin-tweaks.svg)](https://jitpack.io/#umjammer/vavi-idea-plugin-tweaks)
[![Java CI](https://github.com/umjammer/vavi-idea-plugin-tweaks/actions/workflows/maven.yml/badge.svg)](https://github.com/umjammer/vavi-idea-plugin-tweaks/actions/workflows/maven.yml)
[![CodeQL](https://github.com/umjammer/vavi-idea-plugin-tweaks/actions/workflows/codeql.yml/badge.svg)](https://github.com/umjammer/vavi-idea-plugin-tweaks/actions/workflows/codeql.yml)
![Java](https://img.shields.io/badge/Java-25-b07219)

# vavi-idea-plugin-tweaks

small tweaks for intellij idea java editing

## Install

 * `mvn package`, then install `target/vavi-idea-plugin-tweaks-*.zip` by "Settings → Plugins → ⚙ → Install Plugin from Disk..."
 * [maven](https://jitpack.io/#umjammer/vavi-idea-plugin-tweaks)

## Usage

 * structure view (junit5)
   * a `@DisplayName` value is shown at right of a method (or a `@Nested` class) name
   * speed search in the structure view also matches display names
 * java editor
   * click a class or an interface in `extends`/`implements`, methods overriding/implementing it are highlighted
   * the highlights are removed by the next click, escape or a text change
 * intentions/quick fixes
   * the alt+enter popup and the top fix of a problem tooltip are ordered by how many times you applied them
   * counts are stored in `vaviTweaksIntentionUsage.xml` in the ide config
   * java files only (`intentionsOrderProvider` is a per language extension)
 * markdown editor
   * the editor/preview split follows "Settings → Languages & Frameworks → Markdown → Preview layout"
     * the ide ignores "Split horizontally" (preview below the editor) and always splits side by side,
       or uses the orientation saved per file in the workspace
   * the split button on the editor's toolbar changes the orientation of that editor (until it is reopened
     or the setting is changed)

## References

- [jb neglect of intentions/quick fixes 14y](https://youtrack.jetbrains.com/issue/IDEA-88512/I-want-to-be-able-to-customize-the-order-of-quickfixes-intention-actions)

## TODO

 * intention ordering for other languages (kotlin etc.)
 * `IntentionsOrderProvider` is an `impl` package api, may break on ide updates
