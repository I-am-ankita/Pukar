package in.pukar.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessMinutes;
    private final long refreshDays;
    private final long citizenMinutes;

    public JwtService(
            @Value("${pukar.jwt.secret}") String secret,
            @Value("${pukar.jwt.access-token-minutes}") long accessMinutes,
            @Value("${pukar.jwt.refresh-token-days}") long refreshDays,
            @Value("${pukar.jwt.citizen-token-minutes:30}") long citizenMinutes) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessMinutes = accessMinutes;
        this.refreshDays = refreshDays;
        this.citizenMinutes = citizenMinutes;
    }

    public String generateAccessToken(String username, Map<String, Object> claims) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + accessMinutes * 60_000);
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(String username) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + refreshDays * 86_400_000L);
        return Jwts.builder()
                .subject(username)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    public long getAccessTokenSeconds() {
        return accessMinutes * 60;
    }

    /** Short-lived token proving a citizen controls a given reporterHash (issued after OTP verify). */
    public String generateCitizenToken(String reporterHash) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + citizenMinutes * 60_000);
        return Jwts.builder()
                .subject(reporterHash)
                .claim("type", "CITIZEN")
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    public long getCitizenTokenSeconds() {
        return citizenMinutes * 60;
    }

    /** Returns the reporterHash carried by a citizen token, or throws if it is not a valid citizen token. */
    public String parseCitizenReporterHash(String token) {
        Claims claims = parse(token);
        if (!"CITIZEN".equals(claims.get("type", String.class))) {
            throw new io.jsonwebtoken.JwtException("Not a citizen token");
        }
        return claims.getSubject();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUsername(String token) {
        return parse(token).getSubject();
    }
}
