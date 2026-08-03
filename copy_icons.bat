@echo off
set SRC=c:\Users\ASUS\Desktop\Hisab\easyappicon-icons-1785605604445\android
set DST=c:\Users\ASUS\Desktop\Hisab\app\src\main\res

:: Delete old webp launcher files
del /q "%DST%\mipmap-mdpi\*.webp" 2>nul
del /q "%DST%\mipmap-hdpi\*.webp" 2>nul
del /q "%DST%\mipmap-xhdpi\*.webp" 2>nul
del /q "%DST%\mipmap-xxhdpi\*.webp" 2>nul
del /q "%DST%\mipmap-xxxhdpi\*.webp" 2>nul

:: Copy new PNG launcher files
copy /y "%SRC%\mipmap-mdpi\Hisab.png" "%DST%\mipmap-mdpi\ic_launcher.png"
copy /y "%SRC%\mipmap-mdpi\Hisab_foreground.png" "%DST%\mipmap-mdpi\ic_launcher_foreground.png"
copy /y "%SRC%\mipmap-mdpi\Hisab_round.png" "%DST%\mipmap-mdpi\ic_launcher_round.png"

copy /y "%SRC%\mipmap-hdpi\Hisab.png" "%DST%\mipmap-hdpi\ic_launcher.png"
copy /y "%SRC%\mipmap-hdpi\Hisab_foreground.png" "%DST%\mipmap-hdpi\ic_launcher_foreground.png"
copy /y "%SRC%\mipmap-hdpi\Hisab_round.png" "%DST%\mipmap-hdpi\ic_launcher_round.png"

copy /y "%SRC%\mipmap-xhdpi\Hisab.png" "%DST%\mipmap-xhdpi\ic_launcher.png"
copy /y "%SRC%\mipmap-xhdpi\Hisab_foreground.png" "%DST%\mipmap-xhdpi\ic_launcher_foreground.png"
copy /y "%SRC%\mipmap-xhdpi\Hisab_round.png" "%DST%\mipmap-xhdpi\ic_launcher_round.png"

copy /y "%SRC%\mipmap-xxhdpi\Hisab.png" "%DST%\mipmap-xxhdpi\ic_launcher.png"
copy /y "%SRC%\mipmap-xxhdpi\Hisab_foreground.png" "%DST%\mipmap-xxhdpi\ic_launcher_foreground.png"
copy /y "%SRC%\mipmap-xxhdpi\Hisab_round.png" "%DST%\mipmap-xxhdpi\ic_launcher_round.png"

copy /y "%SRC%\mipmap-xxxhdpi\Hisab.png" "%DST%\mipmap-xxxhdpi\ic_launcher.png"
copy /y "%SRC%\mipmap-xxxhdpi\Hisab_foreground.png" "%DST%\mipmap-xxxhdpi\ic_launcher_foreground.png"
copy /y "%SRC%\mipmap-xxxhdpi\Hisab_round.png" "%DST%\mipmap-xxxhdpi\ic_launcher_round.png"

copy /y "%SRC%\values\ic_launcher_background.xml" "%DST%\values\ic_launcher_background.xml"

if not exist "%DST%\mipmap-anydpi-v26" mkdir "%DST%\mipmap-anydpi-v26"
copy /y "%SRC%\mipmap-anydpi-v26\ic_launcher.xml" "%DST%\mipmap-anydpi-v26\ic_launcher.xml"
copy /y "%SRC%\mipmap-anydpi-v26\ic_launcher_round.xml" "%DST%\mipmap-anydpi-v26\ic_launcher_round.xml"

echo Cleaned webp and copied PNG app icons successfully!
