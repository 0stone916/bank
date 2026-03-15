package com.bank.bankmock.common.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class CachedRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        // 원본 스트림을 읽어서 바이트 배열에 복사 (이 시점에 원본 스트림은 소비됨)
        InputStream is = request.getInputStream();
        this.cachedBody = StreamUtils.copyToByteArray(is);
        System.out.println("CachedRequestWrapper 생성자에서 생성됨");
    }

    @Override
    public ServletInputStream getInputStream() {
        // 복사해둔 바이트 배열을 바탕으로 새로운 스트림을 생성해서 반환
        // 덕분에 컨트롤러나 다른 필터에서 몇 번을 호출해도 데이터가 유지됨
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBody);

        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // 비동기 처리 시 필요한 설정이나, 일반적인 상황에선 비워둡니다.
            }

            @Override
            public int read() throws IOException {
                return byteArrayInputStream.read();
            }
        };
    }

    // 복사된 바이트를 문자열로 반환하는 메서드 (보안 검사 및 로깅용)
    public String getBody() {
        return new String(cachedBody, StandardCharsets.UTF_8);
    }
}