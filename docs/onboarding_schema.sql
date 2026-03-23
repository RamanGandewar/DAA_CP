PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS app_users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_key TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL DEFAULT '',
    onboarding_completed INTEGER NOT NULL DEFAULT 0 CHECK (onboarding_completed IN (0, 1)),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_profiles (
    user_id INTEGER PRIMARY KEY,
    full_name TEXT NOT NULL,
    age_group TEXT NOT NULL CHECK (age_group IN ('student', 'working professional', 'other')),
    occupation TEXT NOT NULL,
    default_budget_range TEXT NOT NULL CHECK (default_budget_range IN ('low', 'medium', 'high')),
    preferred_cafe_type TEXT NOT NULL CHECK (
        preferred_cafe_type IN ('quiet / work-friendly', 'social / hangout', 'premium / aesthetic')
    ),
    preferred_distance_km INTEGER NOT NULL CHECK (preferred_distance_km IN (1, 3, 5, 10)),
    dietary_preference TEXT NOT NULL CHECK (
        dietary_preference IN ('vegetarian', 'non-vegetarian', 'vegan', 'no preference')
    ),
    dominant_profile_tag TEXT NOT NULL CHECK (
        dominant_profile_tag IN ('study_work', 'social_hangout', 'date_aesthetic', 'quick_coffee')
    ),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_social_preferences (
    user_id INTEGER PRIMARY KEY,
    usually_visit_with TEXT NOT NULL CHECK (
        usually_visit_with IN ('alone', 'with friends', 'with colleagues', 'with partner')
    ),
    preferred_seating TEXT NOT NULL CHECK (
        preferred_seating IN ('indoor', 'outdoor', 'doesn''t matter')
    ),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_ambience_preferences (
    user_id INTEGER PRIMARY KEY,
    music_preference TEXT NOT NULL CHECK (
        music_preference IN ('silent', 'light music', 'loud / party vibe')
    ),
    lighting_preference TEXT NOT NULL CHECK (
        lighting_preference IN ('bright', 'cozy', 'aesthetic / dim')
    ),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS visit_contexts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    purpose_of_visit TEXT NOT NULL CHECK (
        purpose_of_visit IN ('work / study', 'casual hangout', 'date', 'coffee break', 'meeting')
    ),
    current_budget_range TEXT NOT NULL CHECK (current_budget_range IN ('low', 'medium', 'high')),
    travel_distance_km INTEGER NOT NULL CHECK (travel_distance_km IN (1, 3, 5, 10)),
    time_of_visit TEXT NOT NULL CHECK (
        time_of_visit IN ('morning', 'afternoon', 'evening', 'late night')
    ),
    crowd_tolerance TEXT NOT NULL CHECK (
        crowd_tolerance IN ('quiet', 'moderate', 'lively')
    ),
    is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS recommendation_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    visit_context_id INTEGER,
    source TEXT NOT NULL CHECK (source IN ('csv', 'xlsx')),
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    radius_km REAL NOT NULL,
    budget REAL NOT NULL,
    top_k INTEGER NOT NULL,
    result_count INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE,
    FOREIGN KEY (visit_context_id) REFERENCES visit_contexts(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS recommendation_explanations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    recommendation_history_id INTEGER NOT NULL,
    cafe_id TEXT NOT NULL,
    rank_position INTEGER NOT NULL,
    explanation_text TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (recommendation_history_id) REFERENCES recommendation_history(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_app_users_user_key
ON app_users(user_key);

CREATE INDEX IF NOT EXISTS idx_visit_contexts_user_id
ON visit_contexts(user_id);

CREATE INDEX IF NOT EXISTS idx_visit_contexts_user_id_active
ON visit_contexts(user_id, is_active, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_recommendation_history_user_id
ON recommendation_history(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_recommendation_explanations_history_id
ON recommendation_explanations(recommendation_history_id, rank_position);
