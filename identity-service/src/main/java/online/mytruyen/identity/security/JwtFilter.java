package online.mytruyen.identity.security;

import online.mytruyen.identity.exception.ApiError;
import online.mytruyen.identity.service.IdentityStore;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.io.IOException;
import java.util.UUID;
import io.jsonwebtoken.JwtException;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final IdentityStore store;
    public JwtFilter(JwtService jwt,IdentityStore store) { this.jwt=jwt; this.store=store; }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String header=req.getHeader("Authorization");
        if(header!=null && header.startsWith("Bearer ")) {
            try {
                var claims=jwt.verify(header.substring(7));
                UUID userId=UUID.fromString(claims.getSubject()), sid=UUID.fromString(claims.get("sid",String.class));
                if(!store.activeSession(userId,sid)) throw new ApiError(401,"Session revoked");
                var authorities=store.roles(userId).stream().map(r->new SimpleGrantedAuthority("ROLE_"+r)).toList();
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(new IdentityPrincipal(userId,sid),null,authorities));
            } catch(JwtException | IllegalArgumentException | ApiError e) {
                SecurityContextHolder.clearContext();
                failure(res,401,"Invalid or expired token"); return;
            }
        }
        chain.doFilter(req,res);
    }
    static void failure(HttpServletResponse res,int status,String message) throws IOException {
        res.setStatus(status); res.setContentType("application/json");
        res.getWriter().write("{\"status_code\":"+status+",\"success\":false,\"message\":\""+message+"\",\"data\":null}");
    }
}
