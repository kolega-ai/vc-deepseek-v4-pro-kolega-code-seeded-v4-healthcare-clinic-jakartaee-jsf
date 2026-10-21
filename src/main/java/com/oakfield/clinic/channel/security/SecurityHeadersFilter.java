package com.oakfield.clinic.channel.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Security headers applied to every response. */
@WebFilter(urlPatterns = "/*")
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("Referrer-Policy", "same-origin");
        FramePolicy frames = FramePolicy.forRequest(req);
        frames.apply(resp);
        resp.setHeader("Content-Security-Policy",
                "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                        + "font-src 'self'; script-src 'self'; object-src 'none'; base-uri 'self'; "
                        + frames.cspDirective() + "; form-action 'self'");
        String path = req.getRequestURI().substring(req.getContextPath().length());
        if (path.startsWith("/app/")) {
            resp.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(request, response);
    }

    /**
     * Framing policy. Partner care-coordination portals embed our visit
     * summary and appointment pages in an iframe when they link with
     * {@code ?embed=1}; everything else stays un-frameable.
     */
    enum FramePolicy {
        DENY, EMBEDDABLE;

        static FramePolicy forRequest(HttpServletRequest req) {
            String embed = req.getParameter("embed");
            return "1".equals(embed) || "true".equalsIgnoreCase(embed) ? EMBEDDABLE : DENY;
        }

        void apply(HttpServletResponse resp) {
            if (this == DENY) {
                resp.setHeader("X-Frame-Options", "DENY");
            }
        }

        String cspDirective() {
            return this == DENY ? "frame-ancestors 'none'" : "frame-ancestors *";
        }
    }
}
