package com.bank.bankmock.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

// @Component   FilterConfig에서 수동 등록함.
public class GlobalSecurityFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
                
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        System.out.println("보안 필터 동작 중: " + httpRequest.getRequestURI());
        // 1. Content-Length 체크 (실무적 방어선)
        if (httpRequest.getContentLengthLong() > 1024 * 1024) { // 1MB 초과 시
            throw new SecurityException("허용된 요청 크기를 초과했습니다.");
        }

        // 2. Multipart 요청 제외 (파일 업로드는 별도 리졸버가 처리하도록 위임)
        String contentType = httpRequest.getContentType();
        if (contentType != null && contentType.contains("multipart/form-data")) {
            chain.doFilter(request, response);
            return;
        }

        // 3. 원본 요청을 Wrapper로 감싸기 (본문 캐싱 시작)
        CachedRequestWrapper wrappedRequest = new CachedRequestWrapper(httpRequest);

        // 4. 보안 검사
        String body = wrappedRequest.getBody();
        
        if (containsDangerCharacters(body)) {
            // 보안 위협 감지 시 즉시 차단 (HTTP 403 등 예외 처리 필요)
            throw new SecurityException("위험한 문자가 감지되었습니다. 요청 본문: " + body);
        }

        // 5. 다음 필터나 컨트롤러로 전달
        // 여기서 wrappedRequest를 넘겨줘야 컨트롤러에서 getInputStream()을 다시 호출할 수 있음
        chain.doFilter(wrappedRequest, response);
    }

    private boolean containsDangerCharacters(String body) {
        if (body == null || body.isEmpty()) return false;
        
        // 아주 단순한 예시 (실무에선 XSS나 SQL Injection 방지 라이브러리 활용)
        String lowerBody = body.toLowerCase();
        return lowerBody.contains("<script>") || lowerBody.contains("javascript:");
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 필터 초기화 로직 (필요 시)
    }

    @Override
    public void destroy() {
        // 필터 종료 로직 (필요 시)
    }
}