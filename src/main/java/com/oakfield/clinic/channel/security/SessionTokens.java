package com.oakfield.clinic.channel.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Opaque signed session token cookie (HttpOnly, SameSite=Lax). */
public final class SessionTokens {

    public static final String COOKIE = "OF_SESSION";
    public static final int MAX_AGE_SECONDS = 12 * 3600;

    private SessionTokens() { }

    public static void set(HttpServletResponse response, String token) {
        set(response, token, MAX_AGE_SECONDS);
    }

    public static void set(HttpServletResponse response, String token, int maxAgeSeconds) {
        Cookie cookie = new Cookie(COOKIE, token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    public static void clear(HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    public static String get(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (COOKIE.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    /** Open-redirect guard: only internal app pages are accepted as return targets. */
    public static boolean validReturnTo(String target) {
        if (target == null) return false;
        return target.matches("/app/[A-Za-z0-9/._-]*") || "/login.xhtml".equals(target);
    }
}
