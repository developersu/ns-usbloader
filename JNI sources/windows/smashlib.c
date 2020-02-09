/*
* Derivative & modified code based on awesome example from here: https://www.velleman.eu/images/tmp/usbfind.c
* And MSDN documentation :)
*
* return
*     -2 device not connected
*     -1 Unable to open handler
*      0 maybe we're all set
*/

#ifdef __cplusplus
extern "C" {
#endif

#define _DEBUG
#define _BUILD_FOR_X86

#include <windows.h>
#include <tchar.h>
#include <setupapi.h>
#ifdef DEBUG
#include <stdio.h>
#endif

#ifdef BUILD_FOR_X86
#include <ntddser.h>
#endif

#include "nsusbloader_Utilities_RcmSmash.h"

#define LIBUSB_IOCTL_GET_STATUS CTL_CODE(FILE_DEVICE_UNKNOWN, 0x807, METHOD_BUFFERED, FILE_ANY_ACCESS) 

static GUID GUID_DEVINTERFACE_USB_DEVICE = {0xA5DCBF10L, 0x6530, 0x11D2, {0x90, 0x1F, 0x00, 0xC0, 0x4F, 0xB9, 0x51, 0xED}};
// NOTE: CHANGE TO NV DEVICE!
//static TCHAR VID_PID_PAIR[] = _T("vid_1a86&pid_7523");	// UNCOMMENT FOR TESTS
static TCHAR VID_PID_PAIR[] = _T("vid_0955&pid_7321"); // UNCOMMENT ON RELEASE

typedef struct
{
	unsigned int timeout;
	unsigned int recipient;
	unsigned int index;
	unsigned int status;
	unsigned int ___zeroes_hold_0; // made it for better understanding. Literally useless.
	unsigned int ___zeroes_hold_1; // Just consider each int as 4 bytes and calculate size =)
} simple_status_req;

int win32_magic(LPCSTR lpFileName){
	unsigned char reqBuf[24] = {0};

	simple_status_req* request;
	request = (simple_status_req *) &reqBuf;
		request->timeout = 1000;
		request->recipient = 0x02;
		request->index = 0;
		request->status = 0;

#ifdef DEBUG
	printf("Device path:   %s\nStatus: %x\nIn buffer size: %d\nIn buffer content: ", 
		lpFileName, 
		LIBUSB_IOCTL_GET_STATUS,
		sizeof(reqBuf) ); // Path and what is our IOCTL request looks like

	for (int i = 0; i < sizeof(reqBuf); i++)
		printf("%x ", reqBuf[i]);
	printf("\n");
#endif
	unsigned char outBuffer[28672];

	OVERLAPPED ovrlpd;
	memset(&ovrlpd, 0, sizeof(ovrlpd));
	// Fucking finally let's open this
	HANDLE handler = CreateFile(
		lpFileName, 
		GENERIC_READ | GENERIC_WRITE, 
		FILE_SHARE_READ | FILE_SHARE_WRITE, 
		NULL, 
		OPEN_EXISTING, 
		FILE_FLAG_OVERLAPPED, 
		NULL
	);

	if ( handler == INVALID_HANDLE_VALUE )  
	        return -1;

	BOOL ret_val = DeviceIoControl(
		handler,
		LIBUSB_IOCTL_GET_STATUS,
		(LPVOID) &reqBuf,
		24,
		(LPVOID) &outBuffer,
		28672,
		NULL,
		&ovrlpd
	);
	
#ifdef DEBUG
	printf("\nDeviceIoControl reports: %d\nLast Error Code: %d\n", ret_val, GetLastError());
#endif
	Sleep(250);
	DWORD bReceived = 0;
	ret_val = GetOverlappedResult(handler, &ovrlpd, &bReceived, FALSE);
#ifdef DEBUG
	if (! ret_val) {
		// we won't report any issues since there is no workaround.
		printf("\nLast Error Code: %d\n\n", GetLastError());
	}
#endif
	CloseHandle(handler);

	return 0;
}

JNIEXPORT jint JNICALL Java_nsusbloader_Utilities_RcmSmash_smashWindows
  (JNIEnv * jnie_enb, jclass this_class) {
	int found = 0;
	int ret_val = -2;
	HDEVINFO                         hDevInfo;
	SP_DEVICE_INTERFACE_DATA         DevIntfData;
	PSP_DEVICE_INTERFACE_DETAIL_DATA DevIntfDetailData;
	SP_DEVINFO_DATA                  DevData;

	DWORD dwSize, dwType, dwMemberIdx;
	HKEY hKey;
	BYTE lpData[1024];

	hDevInfo = SetupDiGetClassDevs(&GUID_DEVINTERFACE_USB_DEVICE, NULL, 0, DIGCF_DEVICEINTERFACE | DIGCF_PRESENT);
	
	if (hDevInfo != INVALID_HANDLE_VALUE){	
		DevIntfData.cbSize = sizeof(SP_DEVICE_INTERFACE_DATA);
		dwMemberIdx = 0;
		
		SetupDiEnumDeviceInterfaces(hDevInfo, NULL, &GUID_DEVINTERFACE_USB_DEVICE, dwMemberIdx, &DevIntfData);

		while(GetLastError() != ERROR_NO_MORE_ITEMS) {
			DevData.cbSize = sizeof(DevData);
			
			SetupDiGetDeviceInterfaceDetail(hDevInfo, &DevIntfData, NULL, 0, &dwSize, NULL);

			DevIntfDetailData = HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY, dwSize);
			DevIntfDetailData->cbSize = sizeof(SP_DEVICE_INTERFACE_DETAIL_DATA);

			if (SetupDiGetDeviceInterfaceDetail(hDevInfo, &DevIntfData, DevIntfDetailData, dwSize, &dwSize, &DevData)) {
				if (NULL != _tcsstr((TCHAR*)DevIntfDetailData->DevicePath, VID_PID_PAIR)) {
					found = 1;
					ret_val = win32_magic(DevIntfDetailData->DevicePath);
				}
			}

			HeapFree(GetProcessHeap(), 0, DevIntfDetailData);
			if (found)
				break;
			// Continue looping
			SetupDiEnumDeviceInterfaces(hDevInfo, NULL, &GUID_DEVINTERFACE_USB_DEVICE, ++dwMemberIdx, &DevIntfData);
		}
		SetupDiDestroyDeviceInfoList(hDevInfo);
	}

#ifdef DEBUG
	printf("Returning value: %d\n", ret_val);
#endif
	return ret_val;
}

JNIEXPORT jint JNICALL Java_nsusbloader_Utilities_RcmSmash_smashLinux
  (JNIEnv * jnie_env, jclass this_class, jint bus_id, jint device_addr){
	return -1;
}
