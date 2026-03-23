package db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import model.AmbiencePreference;
import model.AppUser;
import model.DietaryPreference;
import model.OnboardingProfile;
import model.ProfileTag;
import model.Recommendation;
import model.RecommendationHistoryEntry;
import model.SocialPreference;
import model.StoredUserProfile;
import model.StoredVisitContext;
import model.UserProfile;
import model.VisitContext;
import score.OnboardingScorer;

public class SQLiteOnboardingRepository implements OnboardingRepository {
    @Override
    public AppUser createOrGetUser(String userKey, String displayName) {
        Optional<AppUser> existing = findUserByKey(userKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        final String sql = """
                INSERT INTO app_users(user_key, display_name, onboarding_completed)
                VALUES (?, ?, 0)
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, normalizeKey(userKey));
            ps.setString(2, safe(displayName));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return requireUserById(connection, keys.getLong(1));
                }
            }
            throw new SQLException("Failed to create user record.");
        } catch (SQLException e) {
            throw new RepositoryException("Unable to create or fetch app user.", e);
        }
    }

    @Override
    public Optional<AppUser> findUserByKey(String userKey) {
        final String sql = """
                SELECT id, user_key, display_name, onboarding_completed, created_at, updated_at
                FROM app_users
                WHERE user_key = ?
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, normalizeKey(userKey));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapAppUser(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Unable to find app user by key.", e);
        }
    }

    @Override
    public StoredUserProfile saveUserProfile(long userId, UserProfile userProfile) {
        VisitContext contextForTag = VisitContext.empty();
        ProfileTag profileTag = OnboardingScorer.classifyProfile(userProfile, contextForTag);
        final String sql = """
                INSERT INTO user_profiles(
                    user_id, full_name, age_group, occupation, default_budget_range,
                    preferred_cafe_type, preferred_distance_km, dietary_preference,
                    dominant_profile_tag
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(user_id) DO UPDATE SET
                    full_name = excluded.full_name,
                    age_group = excluded.age_group,
                    occupation = excluded.occupation,
                    default_budget_range = excluded.default_budget_range,
                    preferred_cafe_type = excluded.preferred_cafe_type,
                    preferred_distance_km = excluded.preferred_distance_km,
                    dietary_preference = excluded.dietary_preference,
                    dominant_profile_tag = excluded.dominant_profile_tag
                """;
        final String markUserSql = """
                UPDATE app_users
                SET display_name = ?, onboarding_completed = 1
                WHERE id = ?
                """;

        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 PreparedStatement markUser = connection.prepareStatement(markUserSql)) {
                ps.setLong(1, userId);
                ps.setString(2, safe(userProfile.getName()));
                ps.setString(3, toAgeGroupDb(userProfile.getAgeGroup()));
                ps.setString(4, safe(userProfile.getOccupation()));
                ps.setString(5, toBudgetRangeDb(userProfile.getDefaultBudgetRange()));
                ps.setString(6, toCafeTypeDb(userProfile.getPreferredCafeType()));
                ps.setInt(7, toDistanceKm(userProfile.getPreferredDistanceKm()));
                ps.setString(8, toDietDb(userProfile.getDietaryPreference()));
                ps.setString(9, toProfileTagDb(profileTag));
                ps.executeUpdate();

                markUser.setString(1, safe(userProfile.getName()));
                markUser.setLong(2, userId);
                markUser.executeUpdate();

                connection.commit();
                return requireStoredUserProfile(connection, userId);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Unable to save user profile.", e);
        }
    }

    @Override
    public SocialPreference saveSocialPreference(long userId, SocialPreference socialPreference) {
        final String sql = """
                INSERT INTO user_social_preferences(user_id, usually_visit_with, preferred_seating)
                VALUES (?, ?, ?)
                ON CONFLICT(user_id) DO UPDATE SET
                    usually_visit_with = excluded.usually_visit_with,
                    preferred_seating = excluded.preferred_seating
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, toVisitWithDb(socialPreference.getUsuallyVisitWith()));
            ps.setString(3, toSeatingDb(socialPreference.getPreferredSeating()));
            ps.executeUpdate();
            return requireSocialPreference(connection, userId);
        } catch (SQLException e) {
            throw new RepositoryException("Unable to save social preference.", e);
        }
    }

    @Override
    public AmbiencePreference saveAmbiencePreference(long userId, AmbiencePreference ambiencePreference) {
        final String sql = """
                INSERT INTO user_ambience_preferences(user_id, music_preference, lighting_preference)
                VALUES (?, ?, ?)
                ON CONFLICT(user_id) DO UPDATE SET
                    music_preference = excluded.music_preference,
                    lighting_preference = excluded.lighting_preference
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, toMusicDb(ambiencePreference.getMusicPreference()));
            ps.setString(3, toLightingDb(ambiencePreference.getLightingPreference()));
            ps.executeUpdate();
            return requireAmbiencePreference(connection, userId);
        } catch (SQLException e) {
            throw new RepositoryException("Unable to save ambience preference.", e);
        }
    }

    @Override
    public StoredVisitContext saveVisitContext(long userId, VisitContext visitContext, boolean active) {
        final String deactivateSql = "UPDATE visit_contexts SET is_active = 0 WHERE user_id = ?";
        final String insertSql = """
                INSERT INTO visit_contexts(
                    user_id, purpose_of_visit, current_budget_range, travel_distance_km,
                    time_of_visit, crowd_tolerance, is_active
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement deactivate = connection.prepareStatement(deactivateSql);
                 PreparedStatement insert = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                if (active) {
                    deactivate.setLong(1, userId);
                    deactivate.executeUpdate();
                }

                insert.setLong(1, userId);
                insert.setString(2, toPurposeDb(visitContext.getPurposeOfVisit()));
                insert.setString(3, toBudgetRangeDb(visitContext.getCurrentBudgetRange()));
                insert.setInt(4, toDistanceKm(visitContext.getTravelDistanceKm()));
                insert.setString(5, toTimeDb(visitContext.getTimeOfVisit()));
                insert.setString(6, toCrowdDb(visitContext.getCrowdTolerance()));
                insert.setInt(7, active ? 1 : 0);
                insert.executeUpdate();

                long contextId;
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Visit context insert did not return a generated key.");
                    }
                    contextId = keys.getLong(1);
                }
                connection.commit();
                return requireVisitContext(connection, contextId);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Unable to save visit context.", e);
        }
    }

    @Override
    public Optional<OnboardingProfile> findOnboardingProfile(String userKey) {
        Optional<AppUser> user = findUserByKey(userKey);
        if (user.isEmpty()) {
            return Optional.empty();
        }

        AppUser appUser = user.get();
        try (Connection connection = DatabaseManager.getConnection()) {
            StoredUserProfile storedUserProfile = findStoredUserProfile(connection, appUser.getId()).orElse(null);
            SocialPreference socialPreference = findSocialPreference(connection, appUser.getId()).orElse(SocialPreference.empty());
            AmbiencePreference ambiencePreference = findAmbiencePreference(connection, appUser.getId()).orElse(AmbiencePreference.empty());
            StoredVisitContext activeVisitContext = findActiveVisitContext(connection, appUser.getId()).orElse(null);
            return Optional.of(new OnboardingProfile(appUser, storedUserProfile, socialPreference, ambiencePreference, activeVisitContext));
        } catch (SQLException e) {
            throw new RepositoryException("Unable to fetch onboarding profile.", e);
        }
    }

    @Override
    public Optional<StoredVisitContext> findActiveVisitContext(long userId) {
        try (Connection connection = DatabaseManager.getConnection()) {
            return findActiveVisitContext(connection, userId);
        } catch (SQLException e) {
            throw new RepositoryException("Unable to fetch active visit context.", e);
        }
    }

    @Override
    public RecommendationHistoryEntry saveRecommendationHistory(long userId,
                                                                Long visitContextId,
                                                                String source,
                                                                double latitude,
                                                                double longitude,
                                                                double radiusKm,
                                                                double budget,
                                                                int topK,
                                                                int resultCount) {
        final String sql = """
                INSERT INTO recommendation_history(
                    user_id, visit_context_id, source, latitude, longitude,
                    radius_km, budget, top_k, result_count
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            if (visitContextId == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setLong(2, visitContextId);
            }
            ps.setString(3, safe(source).toLowerCase(Locale.ROOT));
            ps.setDouble(4, latitude);
            ps.setDouble(5, longitude);
            ps.setDouble(6, radiusKm);
            ps.setDouble(7, budget);
            ps.setInt(8, topK);
            ps.setInt(9, resultCount);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return requireRecommendationHistory(connection, keys.getLong(1));
                }
            }
            throw new SQLException("Recommendation history insert did not return a generated key.");
        } catch (SQLException e) {
            throw new RepositoryException("Unable to save recommendation history.", e);
        }
    }

    @Override
    public void saveRecommendationExplanations(long recommendationHistoryId, List<Recommendation> recommendations) {
        final String deleteSql = "DELETE FROM recommendation_explanations WHERE recommendation_history_id = ?";
        final String insertSql = """
                INSERT INTO recommendation_explanations(
                    recommendation_history_id, cafe_id, rank_position, explanation_text
                )
                VALUES (?, ?, ?, ?)
                """;
        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement(deleteSql);
                 PreparedStatement insert = connection.prepareStatement(insertSql)) {
                delete.setLong(1, recommendationHistoryId);
                delete.executeUpdate();

                int rank = 1;
                for (Recommendation recommendation : recommendations) {
                    insert.setLong(1, recommendationHistoryId);
                    insert.setString(2, recommendation.getCafe().getId());
                    insert.setInt(3, rank++);
                    insert.setString(4, safe(recommendation.getExplanation()));
                    insert.addBatch();
                }
                insert.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Unable to save recommendation explanations.", e);
        }
    }

    private AppUser requireUserById(Connection connection, long userId) throws SQLException {
        final String sql = """
                SELECT id, user_key, display_name, onboarding_completed, created_at, updated_at
                FROM app_users
                WHERE id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapAppUser(rs);
                }
                throw new SQLException("User not found for id=" + userId);
            }
        }
    }

    private StoredUserProfile requireStoredUserProfile(Connection connection, long userId) throws SQLException {
        return findStoredUserProfile(connection, userId)
                .orElseThrow(() -> new SQLException("User profile not found for userId=" + userId));
    }

    private SocialPreference requireSocialPreference(Connection connection, long userId) throws SQLException {
        return findSocialPreference(connection, userId)
                .orElseThrow(() -> new SQLException("Social preference not found for userId=" + userId));
    }

    private AmbiencePreference requireAmbiencePreference(Connection connection, long userId) throws SQLException {
        return findAmbiencePreference(connection, userId)
                .orElseThrow(() -> new SQLException("Ambience preference not found for userId=" + userId));
    }

    private StoredVisitContext requireVisitContext(Connection connection, long contextId) throws SQLException {
        final String sql = """
                SELECT id, user_id, purpose_of_visit, current_budget_range, travel_distance_km,
                       time_of_visit, crowd_tolerance, is_active, created_at
                FROM visit_contexts
                WHERE id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, contextId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapVisitContext(rs);
                }
                throw new SQLException("Visit context not found for id=" + contextId);
            }
        }
    }

    private RecommendationHistoryEntry requireRecommendationHistory(Connection connection, long id) throws SQLException {
        final String sql = """
                SELECT id, user_id, visit_context_id, source, latitude, longitude, radius_km,
                       budget, top_k, result_count, created_at
                FROM recommendation_history
                WHERE id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long visitContextId = rs.getLong("visit_context_id");
                    Long nullableVisitContextId = rs.wasNull() ? null : visitContextId;
                    return new RecommendationHistoryEntry(
                            rs.getLong("id"),
                            rs.getLong("user_id"),
                            nullableVisitContextId,
                            rs.getString("source"),
                            rs.getDouble("latitude"),
                            rs.getDouble("longitude"),
                            rs.getDouble("radius_km"),
                            rs.getDouble("budget"),
                            rs.getInt("top_k"),
                            rs.getInt("result_count"),
                            rs.getString("created_at")
                    );
                }
                throw new SQLException("Recommendation history not found for id=" + id);
            }
        }
    }

    private Optional<StoredUserProfile> findStoredUserProfile(Connection connection, long userId) throws SQLException {
        final String sql = """
                SELECT user_id, full_name, age_group, occupation, default_budget_range,
                       preferred_cafe_type, preferred_distance_km, dietary_preference,
                       dominant_profile_tag, created_at, updated_at
                FROM user_profiles
                WHERE user_id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                UserProfile profile = new UserProfile(
                        rs.getString("full_name"),
                        rs.getString("age_group"),
                        rs.getString("occupation"),
                        rs.getString("default_budget_range"),
                        rs.getString("preferred_cafe_type"),
                        rs.getInt("preferred_distance_km"),
                        fromDietDb(rs.getString("dietary_preference")),
                        "",
                        "",
                        "",
                        ""
                );
                return Optional.of(new StoredUserProfile(
                        rs.getLong("user_id"),
                        profile,
                        fromProfileTagDb(rs.getString("dominant_profile_tag")),
                        rs.getString("created_at"),
                        rs.getString("updated_at")
                ));
            }
        }
    }

    private Optional<SocialPreference> findSocialPreference(Connection connection, long userId) throws SQLException {
        final String sql = """
                SELECT usually_visit_with, preferred_seating
                FROM user_social_preferences
                WHERE user_id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SocialPreference(
                        rs.getString("usually_visit_with"),
                        rs.getString("preferred_seating")
                ));
            }
        }
    }

    private Optional<AmbiencePreference> findAmbiencePreference(Connection connection, long userId) throws SQLException {
        final String sql = """
                SELECT music_preference, lighting_preference
                FROM user_ambience_preferences
                WHERE user_id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new AmbiencePreference(
                        rs.getString("music_preference"),
                        rs.getString("lighting_preference")
                ));
            }
        }
    }

    private Optional<StoredVisitContext> findActiveVisitContext(Connection connection, long userId) throws SQLException {
        final String sql = """
                SELECT id, user_id, purpose_of_visit, current_budget_range, travel_distance_km,
                       time_of_visit, crowd_tolerance, is_active, created_at
                FROM visit_contexts
                WHERE user_id = ? AND is_active = 1
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapVisitContext(rs));
            }
        }
    }

    private AppUser mapAppUser(ResultSet rs) throws SQLException {
        return new AppUser(
                rs.getLong("id"),
                rs.getString("user_key"),
                rs.getString("display_name"),
                rs.getInt("onboarding_completed") == 1,
                rs.getString("created_at"),
                rs.getString("updated_at")
        );
    }

    private StoredVisitContext mapVisitContext(ResultSet rs) throws SQLException {
        VisitContext visitContext = new VisitContext(
                rs.getString("purpose_of_visit"),
                rs.getString("current_budget_range"),
                rs.getInt("travel_distance_km"),
                rs.getString("time_of_visit"),
                rs.getString("crowd_tolerance")
        );
        return new StoredVisitContext(
                rs.getLong("id"),
                rs.getLong("user_id"),
                visitContext,
                rs.getInt("is_active") == 1,
                rs.getString("created_at")
        );
    }

    private static String normalizeKey(String userKey) {
        String normalized = safe(userKey).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("userKey is required.");
        }
        return normalized;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String toAgeGroupDb(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        if (normalized.equals("working_professional")) {
            return "working professional";
        }
        return switch (normalized) {
            case "student", "working professional", "other" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported age group: " + value);
        };
    }

    private static String toBudgetRangeDb(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "low", "medium", "high" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported budget range: " + value);
        };
    }

    private static String toCafeTypeDb(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "quiet", "work-friendly", "quiet / work-friendly", "quiet/work-friendly" -> "quiet / work-friendly";
            case "social", "hangout", "social / hangout", "social/hangout" -> "social / hangout";
            case "premium", "aesthetic", "premium / aesthetic", "premium/aesthetic" -> "premium / aesthetic";
            default -> throw new IllegalArgumentException("Unsupported preferred cafe type: " + value);
        };
    }

    private static int toDistanceKm(int value) {
        return switch (value) {
            case 1, 3, 5, 10 -> value;
            default -> throw new IllegalArgumentException("Unsupported distance km: " + value);
        };
    }

    private static String toDietDb(DietaryPreference preference) {
        if (preference == null) {
            return "no preference";
        }
        return switch (preference) {
            case VEG -> "vegetarian";
            case NON_VEG -> "non-vegetarian";
            case VEGAN -> "vegan";
            case ANY -> "no preference";
        };
    }

    private static DietaryPreference fromDietDb(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "vegetarian" -> DietaryPreference.VEG;
            case "non-vegetarian" -> DietaryPreference.NON_VEG;
            case "vegan" -> DietaryPreference.VEGAN;
            default -> DietaryPreference.ANY;
        };
    }

    private static String toProfileTagDb(ProfileTag tag) {
        if (tag == null) {
            return "study_work";
        }
        return switch (tag) {
            case STUDY_WORK -> "study_work";
            case SOCIAL_HANGOUT -> "social_hangout";
            case DATE_AESTHETIC -> "date_aesthetic";
            case QUICK_COFFEE -> "quick_coffee";
        };
    }

    private static ProfileTag fromProfileTagDb(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "social_hangout" -> ProfileTag.SOCIAL_HANGOUT;
            case "date_aesthetic" -> ProfileTag.DATE_AESTHETIC;
            case "quick_coffee" -> ProfileTag.QUICK_COFFEE;
            default -> ProfileTag.STUDY_WORK;
        };
    }

    private static String toVisitWithDb(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "alone" -> "alone";
            case "with friends", "friends" -> "with friends";
            case "with colleagues", "colleagues" -> "with colleagues";
            case "with partner", "partner" -> "with partner";
            default -> throw new IllegalArgumentException("Unsupported social preference: " + value);
        };
    }

    private static String toSeatingDb(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "indoor" -> "indoor";
            case "outdoor" -> "outdoor";
            case "doesn't matter", "doesnt matter", "does not matter" -> "doesn't matter";
            default -> throw new IllegalArgumentException("Unsupported seating preference: " + value);
        };
    }

    private static String toMusicDb(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "silent" -> "silent";
            case "light music", "light" -> "light music";
            case "loud / party vibe", "loud", "party vibe" -> "loud / party vibe";
            default -> throw new IllegalArgumentException("Unsupported music preference: " + value);
        };
    }

    private static String toLightingDb(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "bright" -> "bright";
            case "cozy" -> "cozy";
            case "aesthetic / dim", "aesthetic", "dim" -> "aesthetic / dim";
            default -> throw new IllegalArgumentException("Unsupported lighting preference: " + value);
        };
    }

    private static String toPurposeDb(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "work / study", "work", "study" -> "work / study";
            case "casual hangout", "hangout" -> "casual hangout";
            case "date" -> "date";
            case "coffee break" -> "coffee break";
            case "meeting" -> "meeting";
            default -> throw new IllegalArgumentException("Unsupported purpose of visit: " + value);
        };
    }

    private static String toTimeDb(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "morning", "afternoon", "evening", "late night" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported time of visit: " + value);
        };
    }

    private static String toCrowdDb(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "quiet", "moderate", "lively" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported crowd tolerance: " + value);
        };
    }
}
