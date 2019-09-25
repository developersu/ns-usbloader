package nsusbloader.USB;

import nsusbloader.NSLDataTypes.EFileStatus;

public interface ITransferModule {
    EFileStatus getStatus();
}
