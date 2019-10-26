# NS-USBloader

![License](https://img.shields.io/badge/License-GPLv3-blue.svg) [![Releases](https://img.shields.io/github/downloads/developersu/ns-usbloader/total.svg)]() [![LatestVer](https://img.shields.io/github/release/developersu/ns-usbloader.svg)]()

[Support author](#support-this-app)

NS-USBloader is a PC-side **[Adubbz/TinFoil](https://github.com/Adubbz/Tinfoil/)** (version 0.2.1; USB and Network) and **GoldLeaf** (USB) NSP installer. Replacement for default **usb_install_pc.py**, **remote_install_pc.py** and **GoldTree**.

[Click here for Android version ;)](https://github.com/developersu/ns-usbloader-mobile)

With GUI and cookies. Works on Windows, macOS and Linux.

Sometimes I add new posts about this project [on my home page](https://developersu.blogspot.com/search/label/NS-USBloader).

![Screenshot](https://live.staticflickr.com/65535/48962978677_4c3913e8a9_o.png)

#### License

[GNU General Public License v3](https://github.com/developersu/ns-usbloader/blob/master/LICENSE)

#### Used libraries & resources
* [OpenJFX](https://wiki.openjdk.java.net/display/OpenJFX/Main)
* [usb4java](https://mvnrepository.com/artifact/org.usb4java/usb4java)
* Few icons taken from: [materialdesignicons.com](http://materialdesignicons.com/)

#### List of awesome contributors!

* [wolfposd](https://github.com/wolfposd)

#### Thanks for the great work done by our translators!

* French by [Stephane Meden (JackFromNice)](https://github.com/JackFromNice) 
* Italian by [unbranched](https://github.com/unbranched)
* Korean by [DDinghoya](https://github.com/DDinghoya)
* Portuguese by [almircanella](https://github.com/almircanella)
* Spanish by [/u/cokimaya007](https://www.reddit.com/u/cokimaya007), Kuziel Alejandro
* Chinese by [Huang YunKun (htynkn)](https://github.com/htynkn)
* German by [Swarsele](https://github.com/Swarsele)
* Vietnamese by [Hai Phan Nguyen (pnghai)](https://github.com/pnghai)

### System requirements

JRE/JDK 8u60 or higher.

### Table of supported GoldLeaf versions
| GoldLeaf version | NS-USBloader version |
| ---------------- | -------------------- |
| v0.5             | v0.4 - v0.5.2, v0.8+ |
| v0.6.1           | v0.6                 |
| v0.7             | v0.7 - v0.8+         |

### Usage
##### Linux:

1. Install JRE/JDK 8u60 or higher (openJDK is good. Oracle's one is also good). JavaFX not needed (it's embedded).

2. `root # java -jar /path/to/NS-USBloader.jar`

3. Optional. Add user to 'udev' rules to use NS not-from-root-account
```
root # vim /etc/udev/rules.d/99-NS.rules
SUBSYSTEM=="usb", ATTRS{idVendor}=="057e", ATTRS{idProduct}=="3000", GROUP="plugdev"
root # udevadm control --reload-rules && udevadm trigger
```

##### macOS

Double-click on downloaded .jar file. Follow instructions. Or see 'Linux' section.

Set 'Security & Privacy' settings if needed.

##### Windows: 

* Download Zadig: [https://zadig.akeo.ie/](https://zadig.akeo.ie/)
* Open TinFoil. Set 'Title Management' -> 'Usb install NSP'
* Connect NS to PC
* Open Zadig
* Click 'Options' and select 'List All Devices'
* Select NS in drop-down, select 'libusbK (v3.0.7.0)' (version may vary), click 'Install WCID Driver'
* Check that in device list of you system you have 'libusbK USB Devices' folder and your NS inside of it
* [Download and install Java JRE](http://java.com/download/) (8u60 or higher)
* Get this application (JAR file) double-click on on it (alternatively open 'cmd', go to place where jar located and execute via `java -jar thisAppName.jar`)
* Remember to have fun!

#### And how to use it?

The first thing you should do it install TinFoil ([Adubbz](https://github.com/Adubbz/Tinfoil/)) or GoldLeaf ([XorTroll](https://github.com/XorTroll/Goldleaf)) on your NS.

Here is the version of 'not perfect but anyway' [tinfoil I use](https://cloud.mail.ru/public/DwbX/H8d2p3aYR).
Ok, I'm almost sure that this version has bugs. I don't remember where I downloaded it. But it works for me somehow. 

Let's rephrase, if you have working version of TinFoil **DO NOT** use this one. Ok. let's begin.

Take a look on app, find where is the option to install from USB and/or Network. Maybe [this article](https://developersu.blogspot.com/2019/02/ns-usbloader-en.html) will be helpful.

There are three tabs. First one is main.

##### First tab.

At the top of you selecting from drop-down application and protocol that you're going to use. For GoldLeaf only USB is available. Lamp icon stands for switching themes (light or dark).

Then you may drag-n-drop files (split-files aka folders) to application or use 'Select NSP files' button. Multiple selection for files available. Click it again and select files from another folder it you want, it will be added into the table.

Table.

There you can select checkbox for files that will be send to application (TF/GL). ~~Since GoldLeaf allow you only one file transmission per time, only one file is available for selection.~~ 

Also you can use space to select/un-select files and 'delete' button for deleting. By right-mouse-click you can see context menu where you can delete one OR all items from the table.

For GoldLeaf v0.6.1 and NS-USBloader v0.6 (and higher) you will have to use 'Explore content' -> 'Remote PC (via USB)' You will see two drives HOME:/ and VIRT:/. First drive is pointing to your home directory. Second one is reflection of what you've added to table (first application tab). Also VIRT:/ drive have limited functionality in comparison to HOME:/. E.g. you can't write files to this drive since it's not a drive. But don't worry, it won't make any impact on GoldLeaf or your NS if you try.

Also, for GoldLeaf write files (from NS to PC): You have to 'Stop execution' properly before accessing files transferred from GL. Usually you have to wait 5sec or less. It will guarantee that your files properly written to PC.

##### Second tab.

Here you can configure settings for network file transmission. Usually you shouldn't change anything. But it you're cool hacker, go ahead! The most interesting option here is 'Don't serve requests'. Architecture of the TinFoil's NET part is working interesting way. When you select in TF network NSP transfer, application will wait at port 2000 for the information about where should it take files from. Like '192.168.1.5:6060/my file.nsp'. Usually NS-USBloader serves requests by implementing simplified HTTP server and bringing it up and so on. But if this option selected, you can define path to remote location of the files. For example if you set in settings '192.168.4.2:80/ROMS/NS/' and add in table file 'my file.nsp' then NS-USBloader will simply tell TinFoil "Hey, go take files from '192.168.4.2:80/ROMS/NS/my%20file.nsp' ". Of course you have to bring '192.168.4.2' host up and make file accessible from such address (just go install nginx). As I said, this feature is interesting, but I guess won't be popular.

Also here you can:
* Set 'Auto-check for updates' for checking for updates when application starts, or click button to verify if new version released immediately.
* Set 'Show only *.nsp in GoldLeaf' to filter all files displayed at HOME:/ drive. So only NSP files will appear.

##### Third tab.

That's where all logs dropped. Verbose information about transmissions comes here.

### Known bugs
* 'NET' once started it never ends:

It happens because there is HTTP server inside of application. It can't determine the moment when all transmissions finishes (unless they failed). So you have to look on your NS screen and 'Interrupt' it once done.

* Unable to interrupt transmission when network transmission started and nothing received from NS.

### Other notes
Alternative build for Windows 10 is recommended for all Windows 10 users. It also works well on Linux and any other Windows PC and even on macOS Mojave, but doesn't work on all previous versions of macOS. 

'Status' = 'Uploaded' that appears in the table does not mean that file has been installed. It means that it has been sent to NS without any issues! That's what this app about. 
Handling successful/failed installation is a purpose of the other side application: TinFoil or GoldLeaf. And they don't provide any feedback interfaces so I can't detect success/failure.

usb4java since NS-USBloader-v0.2.3 switched to 1.2.0 instead of 1.3.0. This should not impact anyone except users of macOS High Sierra (and Sierra, and El Capitan) where previous versions of NS-USBloader didn't work. 

### Translators!
If you want to see this app translated to your language, go grab [this file](https://github.com/developersu/ns-usbloader/blob/master/src/main/resources/locale.properties) and translate it.

Upload somewhere (create PR, use pastebin/google drive/whatever else). [Create new issue](https://github.com/developersu/ns-usbloader/issues) and post a link. I'll grab it and add.

To convert files of any locale to readable format (and vise-versa) you can use this site [https://itpro.cz/juniconv/](https://itpro.cz/juniconv/)
 

#### TODO (maybe):
- [x] [Android support](https://github.com/developersu/ns-usbloader-mobile)
- [ ] File order sort (non-critical)
- [ ] More deep file analyze before uploading.

## Support this app

If you like this app, just give a star. 

Want to support development? Make a donation* (see below):

[Yandex.Money](https://money.yandex.ru/to/410014301951665)

[PayPal](https://paypal.me/developersu)

*Legal information:

* [EN] Please note! This is non-commercial application. Any received money considered as a gift.
* [RU] Пожалуйста обратите внимание! Это некоммерческое приложение. Перечисляя средства вы совершаете дарение.

##### Thanks
Appreciate assistance and support of both Vitaliy and Konstantin. Without you all this magic would not have happened.

[Konstanin Kelemen](https://github.com/konstantin-kelemen)

[Vitaliy Natarov](https://github.com/SebastianUA)