package common;

public class Protocol {
    private Protocol() {}

    // ==== Tài khoản ====
    public static final String CMD_REGISTER       = "REGISTER";
    public static final String RESP_REGISTER_OK   = "REGISTER_SUCCESS";
    public static final String RESP_REGISTER_FAIL = "REGISTER_FAILED";
    public static final String CMD_LOGIN          = "LOGIN";

    // ==== Tin nhắn riêng ====
    public static final String CMD_DM             = "DM";
    public static final String CMD_GET_HISTORY    = "GET_HISTORY";
    public static final String RESP_HISTORY       = "HISTORY";

    // ==== Nhóm chat ====
    public static final String CMD_CREATE_GROUP        = "CREATE_GROUP";
    public static final String RESP_GROUP_CREATED      = "GROUP_CREATED";
    public static final String CMD_GET_GROUP_HISTORY   = "GET_GROUP_HISTORY";
    public static final String RESP_GROUP_HISTORY      = "GROUP_HISTORY";
    public static final String CMD_GROUP_MESSAGE       = "GROUP_MSG";

 // ==== Video call ====
    public static final String CMD_VIDEO_CALL_REQUEST   = "VIDEO_CALL_REQUEST";   // client -> server
    public static final String CMD_VIDEO_CALL_ACCEPT    = "VIDEO_CALL_ACCEPT";    // client -> server
    public static final String CMD_VIDEO_CALL_REJECT    = "VIDEO_CALL_REJECT";    // client -> server
    public static final String CMD_VIDEO_CALL_END       = "VIDEO_CALL_END";       // client -> server

    public static final String RESP_VIDEO_CALL_INCOMING = "VIDEO_CALL_INCOMING";  // server -> callee
    public static final String RESP_VIDEO_CALL_ACCEPTED = "VIDEO_CALL_ACCEPTED";  // server -> caller
    public static final String RESP_VIDEO_CALL_REJECTED = "VIDEO_CALL_REJECTED";  // server -> caller
    public static final String RESP_VIDEO_CALL_ENDED    = "VIDEO_CALL_ENDED";     // server -> other side

    public static final String CMD_VIDEO_FRAME          = "VIDEO_FRAME";          // frame data

 // ==== Audio cho video call ====
    public static final String CMD_AUDIO_FRAME = "AUDIO_FRAME";

    
    // ==== Gửi file ====
    public static final String CMD_FILE           = "FILE";

    // ==== Khác ====
    public static final String RESP_SERVER_CLOSE  = "SERVER_CLOSE";
}
