package db;

import java.util.Optional;
import model.AdminOverview;
import model.AppUser;
import model.AuthSession;

public interface AuthRepository {
    AppUser registerUser(String name, String email, String password);
    Optional<AppUser> findUserByEmail(String email);
    Optional<AppUser> findUserBySessionToken(String sessionToken);
    AuthSession login(String email, String password);
    void logout(String sessionToken);
    Optional<AuthSession> findActiveSession(String sessionToken);
    void updateSessionLocation(String sessionToken, Double lat, Double lon, String locationSource);
    AdminOverview getAdminOverview();
}
