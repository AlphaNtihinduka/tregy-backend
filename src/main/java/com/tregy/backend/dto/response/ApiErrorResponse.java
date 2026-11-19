package com.tregy.backend.dto.response;

import java.time.Instant;
import java.util.Map;

// The one error shape for every non-2xx response. `code` is a stable string clients can
// switch on (api-contract-and-versioning); `message` is for humans and never carries
// another user's data, tokens or stack traces.
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String requestId,
        Map<String, String> fieldErrors
) {
    public ApiErrorResponse(Instant timestamp,int status,String error,String code,String message,String path){
        this(timestamp,status,error,code,message,path,null,null);
    }
}
