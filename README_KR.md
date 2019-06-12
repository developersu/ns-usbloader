# NS-USBloader

NS-USBloader는 PC-side **[Adubbz/TinFoil](https://github.com/Adubbz/Tinfoil/)** (버전 0.2.1; USB와 네트워크)와 **GoldLeaf** (USB) NSP 설치 프로그램 입니다. 기본 **usb_install_pc.py**, **remote_install_pc.py** *(당신이 용감할지라도 사용하지 마십시오. 왜 작동하는지 모르겠음)* 와 **GoldTree**를 대체할 수 있습니다.

GUI와 쿠키. Windows, macOS, Linux에서 작동합니다.

가끔씩 이 프로젝트에 대한 새로운 게시물을 [내 홈페이지](https://developersu.blogspot.com/search/label/NS-USBloader). 에 추가합니다.

![스크린샷](https://farm8.staticflickr.com/7809/46703921964_53f60f04ed_o.png)

### 라이센스

[GNU 일반 공개 라이센스 v3](https://github.com/developersu/ns-usbloader/blob/master/LICENSE)

### 사용된 라이브러리 & 리소스
* [OpenJFX](https://wiki.openjdk.java.net/display/OpenJFX/Main)
* [usb4java](https://mvnrepository.com/artifact/org.usb4java/usb4java)
* [materialdesignicons.com](http://materialdesignicons.com/) 에서 가져온 아이콘은 거의 없습니다.

### 시스템 요구사항

JRE/JDK 8u60 또는 이상.

### 사용법
#### 그것을 시작하는 방법.
##### Linux:

1. JRE/JDK 8u60 이상을 설치하십시오 (openJDK는 좋습니다. 오라클의 것도 좋습니다). JavaFX는 필요하지 않습니다 (내장되어 있습니다).

2. `root # java -jar /path/to/NS-USBloader.jar`

##### macOS

다운로드 한 .jar 파일을 더블 클릭하십시오. 지시를 따릅니다. 또는 'Linux' 섹션을 참조하십시오.

필요한 경우 '보안 & 개인 정보' 설정을 지정하십시오.

##### Windows: 

* Zadig 다운로드: [https://zadig.akeo.ie/](https://zadig.akeo.ie/)
* TinFoil 열기. '타이틀 관리' -> 'Usb 설치 NSP' 설정
* NS를 PC에 연결
* Zadig 열기
* '옵션'을 클릭하고 '모든 장치 나열' 선택
* 드롭다운에서 NS를 선택하고 'libusbK (v3.0.7.0)'(버전이 다를 수 있음)을 선택하고 'WCID 드라이버 설치' 클릭.
* 시스템의 장치 목록에서 'libusbK USB Devices' 폴더가 있고 그 안에 NS가 있는지 확인.
* [Java JRE 다운로드 및 설치](http://java.com/download/) (8u60 또는 이상)
* 이 응용 프로그램 (JAR 파일)을 두 번 클릭합니다 (또는 'cmd'를 열고 'java -jar thisAppName.jar'을 통해 jar이 위치한 곳으로 이동).
* 즐거운 시간을 보내십시오!

#### 어떻게 사용합니까?

가장 먼저해야 할 일은 NS에 TinFoil ([Adubbz] (https://github.com/Adubbz/Tinfoil/)) 또는 GoldLeaf ([XorTroll] (https://github.com/XorTroll/Goldleaf)) 를 설치하는 것입니다. 나는 TinFoil을 사용하는 것을 권하지만, 그것은 당신에게 달려있습니다. 앱을보고 USB 및/또는 네트워크에서 설치할 수 있는 옵션을 찾으십시오. 아마 [이 게시물] (https://developersu.blogspot.com/2019/02/ns-usbloader-en.html) 이 도움이 될 것입니다.

여기에 '완벽하지 않지만 어쨌든' [내가 사용하는 tinfoil](https://cloud.mail.ru/public/DwbX/H8d2p3aYR) 이 있습니다.
좋아, 나는 이 버전에 버그가 있다는 것을 거의 확신합니다. 나는 그것을 어디에서 다운로드했는지 기억하지 않지만, 그것은 어떻게든 나를 위해 작동합니다.

TinFoil의 작업 버전을 가지고 있다면 이것을 **사용하지 마십시오**. 좋아요. 시작합니다.

세 개의 탭이 있습니다. 첫번째 것은 메인입니다.

##### 첫 번째 탭.

상단에서 드롭 다운 응용 프로그램과 프로토콜을 선택하여 사용할 것입니다. GoldLeaf의 경우 USB 만 사용할 수 있습니다. 램프 아이콘은 테마 전환 (밝은 색 또는 어두운 색)을 의미합니다.

그런 다음 NSP 또는 파일이 있는 폴더를 응용 프로그램으로 끌어다 놓거나 'NSP 파일 선택' 버튼을 사용할 수 있습니다. 사용 가능한 파일을 여러 번 선택할 수 있습니다. 다시 클릭하고 원하는 다른 폴더에서 파일을 선택하면 테이블에 추가됩니다.

테이블.

여기에서 신청서 (TF/GL)로 보낼 파일에 대한 확인란을 선택할 수 있습니다. GoldLeaf는 시간당 하나의 파일 전송만을 허용하기 때문에 하나의 파일만 선택할 수 있습니다. 또한 공간을 사용하여 파일을 선택/선택 취소하고 삭제를 위해 '삭제' 버튼을 사용할 수 있습니다. 마우스 오른쪽 버튼을 클릭하면 컨텍스트 메뉴가 표시되어 테이블에서 하나 또는 모든 항목을 삭제할 수 있습니다.

##### 두 번째 탭.

네트워크 파일 전송 설정을 구성 할 수 있습니다. 보통 아무것도 바꾸면 안되지만 당신은 멋진 해커이기에 나아가세요! 가장 흥미로운 옵션은 '요청하지 마십시오' 입니다. TinFoil의 NET 부분 아키텍처가 흥미로운 방식으로 작동하고 있습니다. TF 네트워크 NSP 전송을 선택하면 응용 프로그램은 포트 2000에서 파일을 가져 오는 위치에 대한 정보를 기다립니다. '192.168.1.5:6060/my file.nsp'와 같습니다. 일반적으로 NS-USBloader는 단순화 된 HTTP 서버를 구현하고 이를 가져오는 등 요청을 처리하지만, 이 옵션을 선택하면 파일의 원격 위치에 대한 경로를 정의할 수 있습니다. 예를 들어 '192.168.4.2:80/ROMS/NS/' 설정을 지정하고 'my file.nsp' 테이블 파일을 추가하면 NS-USBloader는 TinFoil에 "'192.168.4.2:80/ROMS/NS/my%20file.nsp'에서 파일 가져오기"로 간단히 언급합니다. 물론 당신은 '192.168.4.2' 호스트를 가져와서 그런 주소에서 파일에 접근 할 수 있게해야 합니다 (그냥 nginx를 설치하십시오). 제가 말했듯이, 이 기능은 재미있지만, 인기가 없을 것입니다.

또한 여기에서 '업데이트 자동 확인'을 선택하거나 버튼을 클릭하여 새 버전의 출시 여부를 확인할 수 있습니다.

##### 세 번째 탭.

그것이 모든 로그가 삭제된 곳입니다. 전송에 대한 자세한 정보는 여기에 있습니다.

왜 'NET'이 한 번 시작되면 결코 끝나지 않습니까?

응용 프로그램 내부에 HTTP 서버가 있기 때문입니다. 모든 전송이 (실패하지 않는 한) 끝나는 순간을 결정할 수는 없습니다. 그래서 당신은 당신의 NS 화면을 보아야만 합니다.

### 팁&트릭
#### Linux: NS-from-root-account를 사용하려면 'udev'규칙에 사용자 추가
```
root # vim /etc/udev/rules.d/99-NS.rules
SUBSYSTEM=="usb", ATTRS{idVendor}=="057e", ATTRS{idProduct}=="3000", GROUP="plugdev"
root # udevadm control --reload-rules && udevadm trigger
```

### 알려진 버그
* 읽기 이벤트를 기다리는 libusb (사용자가 NSP 목록을 보냈지만 NS에서 아무 것도 선택하지 않은 경우)를 기다릴 때 전송을 방해할 수 없습니다. 때때로 이 문제는 네트워크 전송이 시작되고 NS로부터 아무것도 수신되지 않은 경우에도 나타납니다.

### 다른 참조
표에 'Status' = 'Uploaded'라고 표시되었다고해서 파일이 설치되었다는 의미는 아닙니다. 그것은 어떤 문제없이 NS에 보냈다는 것을 의미합니다! 그것이 바로 이 앱의 특징입니다.
설치 성공/실패 처리는 상대방 응용 프로그램의 목적입니다: TinFoil 또는 GoldLeaf. 그리고 그들은 피드백 인터페이스를 제공하지 않기 때문에 성공/실패를 감지할 수 없습니다.

NS-USBloader-v0.2.3이 1.3.0 대신 1.2.0으로 전환된 이후 usb4java. 이것은 이전 버전의 NS-USBloader가 작동하지 않는 macOS 하이 시에라 (및 시에라, 엘 캐피탄) 사용자를 제외한 모든 사용자에게 영향을 주지 않습니다.

### Translators! Traductores! Übersetzer! Թարգմանիչներ! 번역가!
이 앱을 귀하의 언어로 번역하려면 [이 파일](https://github.com/developersu/ns-usbloader/blob/master/src/main/resources/locale.properties) 클릭하고 번역하십시오.
어딘가에 업로드 (페이스트빈? 구글 드라이브? 그 밖의 무엇이든지). [새로운 이슈 생성](https://github.com/developersu/ns-usbloader/issues) 와 링크를 게시하십시오. 검토하여 추가할 것 입니다. 

참고: 사실 우리가 연락을 해야하기 때문에 실제로 작동하지 않을 것입니다. 번역해야 할 것을 추가할 때 필요합니다. =( 

### 번역가가 해준 위대한 일에 감사드립니다!

프랑스어 [Stephane Meden (JackFromNice)](https://github.com/JackFromNice) 

이탈리아어 [unbranched](https://github.com/unbranched)

#### (아마도) 할 일:
- [x] macOS QA v0.1  (모하비)
- [x] macOS QA v0.2.2 (모하비)
- [x] macOS QA v0.2.3-DEV (하이 시에라)
- [x] macOS QA v0.3
- [x] 윈도우 지원
- [x] 코드 리팩터링
- [x] GoldLeaf 지원
- [ ] XCI 지원
- [ ] 파일 순서 정렬 (중요하지 않음)
- [ ] 업로드 전 더 심오하게 파일 분석.
- [x] TinFoil에 대한 네트워크 모드 지원
- [x] '응용 프로그램 업데이트 확인' 기능


#### 감사
Vitaliy과 Konstantin의 도움과 지원에 감사드립니다. 당신들 없이는 이 모든 마법은 일어나지 않았을 겁니다.

[Konstanin Kelemen](https://github.com/konstantin-kelemen)

[Vitaliy Natarov](https://github.com/SebastianUA)
