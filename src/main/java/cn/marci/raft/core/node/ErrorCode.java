package cn.marci.raft.core.node;

public enum ErrorCode {
    BUSY(1);


    private int errorCode;

    ErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
