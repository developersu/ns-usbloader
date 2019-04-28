# NS-USBloader

NS-USBloader is a PC-side **[Adubbz/TinFoil](https://github.com/Adubbz/Tinfoil/)** (version 0.2.1; USB and Network) and **GoldLeaf** (USB) NSP installer. Replacement for default **usb_install_pc.py**, **remote_install_pc.py** *(never ever use this. even if you brave. no idea why it works.)* and **GoldTree**.

With GUI and cookies. Works on Windows, macOS and Linux.

Sometimes I add new posts about this project [on my home page](https://developersu.blogspot.com/search/label/NS-USBloader).

![Screenshot](https://farm8.staticflickr.com/7809/46703921964_53f60f04ed_o.png)

### License

[GNU General Public License v3](https://github.com/developersu/ns-usbloader/blob/master/LICENSE)

### Used libraries & resources
* [OpenJFX](https://wiki.openjdk.java.net/display/OpenJFX/Main)
* [usb4java](https://mvnrepository.com/artifact/org.usb4java/usb4java)
* Few icons taken from: [materialdesignicons.com](http://materialdesignicons.com/)

### System requirements

JRE/JDK 8u60 or higher.

### Usage
#### How to start it on..
##### Linux:

1. Install JRE/JDK 8u60 or higher (openJDK is good. Oracle's one is also good). JavaFX not needed (it's embedded).

2. `root # java -jar /path/to/NS-USBloader.jar`

##### macOS

Double-click on downloaded .jar file. Follow instructions. Or see 'Linux' section.

Set 'Security & Privacy' settings if needed.

##### Windows: 

* Download Zadig: [https://zadig.akeo.ie/](https://zadig.akeo.ie/)
* Open TinFoil. Set 'Title Management' -> 'Usb install NSP'
* Connect NS to PC
* Open Zadig
* Click 'Options' and select 'List All Devices'
* Select NS in dropdown, select 'libusbK (v3.0.7.0)' (version may vary), click 'Install WCID Driver'
* Check that in device list of you system you have 'libusbK USB Devices' folder and your NS inside of it
* [Download and install Java JRE](http://java.com/download/) (8u60 or higher)
* Get this application (JAR file) double-click on on it (alternatively open 'cmd', go to place where jar located and execute via `java -jar thisAppName.jar`)
* Remember to have fun!

#### And how to use it?

The first thing you should do it install TinFoil ([Adubbz](https://github.com/Adubbz/Tinfoil/)) or GoldLeaf ([XorTroll](https://github.com/XorTroll/Goldleaf)) on your NS. I recommend using TinFoil, but it ups to you. Take a look on app, find where is the option to install from USB and/or Network. Maybe [this article](https://developersu.blogspot.com/2019/02/ns-usbloader-en.html) will be helpful.

Here is the version of 'not perfect but anyway' [tinfoil I use](https://cloud.mail.ru/public/DwbX/H8d2p3aYR).
Ok, I'm almost sure that this version has bugs. I don't remember where I downloaded it. But it works for me somehow. 

Let's rephrase, if you have working version of TinFoil **DO NOT** use this one. Ok. let's begin.

There are three tabs. First one is main.

##### First tab.

At the top of you selecting from drop-down application and protocol that you're going to use. For GoldLeaf only USB is available. Lamp icon stands for switching themes (light or dark).

Then you may drag-n-drop folder with NSPs OR files to application or use 'Select NSP files' button. Multiple selection for files available. Click it again and select files from another folder it you want, it will be added into the table.

Table.

There you can select checkbox for files that will be send to application (TF/GL). Since GoldLeaf allow you only one file transmission per time, only one file is available for selection. Also you can use space to select/un-select files and 'delete' button for deleting. By right-mouse-click you can see context menu where you can delete one OR all items from the table.

##### Second tab.

Here you can configure settings for network file transmission. Usually you shouldn't change anything. But it you're cool hacker, go ahead! The most interesting option here is 'Don't serve requests'. Architecture of the TinFoil's NET part is working interesting way. When you select in TF network NSP transfer, application will wait at port 2000 for the information about where should it take files from. Like '192.168.1.5:6060/my file.nsp'. Usually NS-USBloader serves requests by implementing simplified HTTP server and bringing it up and so on. But if this option selected, you can define path to remote location of the files. For example if you set in settings '192.168.4.2:80/ROMS/NS/' and add in table file 'my file.nsp' then NS-USBloader will simply tell TinFoil "Hey, go take files from '192.168.4.2:80/ROMS/NS/my%20file.nsp' ". Of course you have to bring '192.168.4.2' host up and make file accessible from such address (just go install nginx). As I said, this feature is interesting, but I guess won't be popular.

Also here you can check 'Auto-check for updates' or click button to verify if new version released or not.

##### Third tab.

That's where all logs dropped. Verbose information about transmissions comes here.

Why when 'NET' once started it never ends?

Because there is HTTP server inside of application. It can't determine the moment when all transmissions finishes (unless they failed). So you have to look on your NS screen and 'Interrupt' it once done.

### Tips&tricks
#### Linux: Add user to 'udev' rules to use NS not-from-root-account
```
root # vim /etc/udev/rules.d/99-NS.rules
SUBSYSTEM=="usb", ATTRS{idVendor}=="057e", ATTRS{idProduct}=="3000", GROUP="plugdev"
root # udevadm control --reload-rules && udevadm trigger
```

### Known bugs
* Unable to interrupt transmission when libusb awaiting for read event (when user sent NSP list but didn't select anything on NS). Sometimes this issue also appears when network transmission started and nothing received from NS.

### Other notes
'Status' = 'Uploaded' that appears in the table does not mean that file has been installed. It means that it has been sent to NS without any issues! That's what this app about. 
Handling successful/failed installation is a purpose of the other side application: TinFoil or GoldLeaf. And they don't provide any feedback interfaces so I can't detect success/failure.

usb4java since NS-USBloader-v0.2.3 switched to 1.2.0 instead of 1.3.0. This should not impact anyone except users of macOS High Sierra (and Sierra, and El Capitan) where previous versions of NS-USBloader didn't work. 

### Translators! Traductores! Übersetzer! Թարգմանիչներ!
If you want to see this app translated to your language, go grab [this file](https://github.com/developersu/ns-usbloader/blob/master/src/main/resources/locale.properties) and translate it.
Upload somewhere (pastebin? google drive? whatever else). [Create new issue](https://github.com/developersu/ns-usbloader/issues) and post a link. I'll grab it and add. 

NOTE: actually it's not gonna work in real, because we should stay in touch and I'll need you when add something that should be translated =(

#### Thanks for great work done by our translator~~s team~~!

Français by [Stephane Meden (JackFromNice)](https://github.com/JackFromNice) 

Italian by [unbranched](https://github.com/unbranched)

#### TODO (maybe):
- [x] macOS QA v0.1  (Mojave)
- [x] macOS QA v0.2.2 (Mojave)
- [x] macOS QA v0.2.3-DEV (High Sierra)
- [x] macOS QA v0.3
- [x] Windows support
- [x] code refactoring
- [x] GoldLeaf support
- [ ] XCI support
- [ ] File order sort (non-critical)
- [ ] More deep file analyze before uploading.
- [x] Network mode support for TinFoil
- [x] 'Check for application updates' functionality


#### Thanks
Appreciate assistance and support of both Vitaliy and Konstantin. Without you all this magic would not have happened.

[Konstanin Kelemen](https://github.com/konstantin-kelemen)

[Vitaliy Natarov](https://github.com/SebastianUA)