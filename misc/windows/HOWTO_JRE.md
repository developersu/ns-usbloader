#### How to prepare JRE from JDK to bundle it with application

1. Run `java --list-modules`
2. Update resulting list s/@.*\n/\,/g
3. Run `jlink --no-header-files --no-man-pages --compress=2 --add-modules !!!_PASTE_RESULT_HERE_!!! --output jre`
4. JRE created at folder 'jre 

jlink --no-header-files --no-man-pages --compress=2 --add-modules $($(java --list-modules) -join "," -replace "@[0-9].*") --output jre-11'