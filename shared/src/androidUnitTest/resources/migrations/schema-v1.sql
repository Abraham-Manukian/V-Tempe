-- AppDatabase schema version 1 (before 1.sqm), taken from the .sq files at b8ad55a^.
-- Synthetic fixture for MigrationEquivalenceTest — never a copy of a user database.
CREATE TABLE Exercise(
  id TEXT NOT NULL PRIMARY KEY,
  name TEXT NOT NULL,
  muscleGroups TEXT, -- JSON array of strings
  difficulty INTEGER NOT NULL
);
CREATE TABLE Meal(
  id TEXT NOT NULL PRIMARY KEY,
  name TEXT NOT NULL,
  kcal INTEGER NOT NULL,
  protein INTEGER NOT NULL,
  fat INTEGER NOT NULL,
  carbs INTEGER NOT NULL
);
CREATE TABLE MealIngredient(
  mealId TEXT NOT NULL REFERENCES Meal(id) ON DELETE CASCADE,
  ingredient TEXT NOT NULL
);
CREATE TABLE MealByDay(
  weekIndex INTEGER NOT NULL,
  day TEXT NOT NULL, -- Mon..Sun
  indexInDay INTEGER NOT NULL,
  mealId TEXT NOT NULL REFERENCES Meal(id) ON DELETE CASCADE,
  PRIMARY KEY(weekIndex, day, indexInDay)
);
CREATE INDEX IF NOT EXISTS idx_mealbyday_week ON MealByDay(weekIndex);
CREATE INDEX IF NOT EXISTS idx_mealingredient_meal ON MealIngredient(mealId);
CREATE TABLE Profile(
  id TEXT NOT NULL PRIMARY KEY,
  age INTEGER NOT NULL,
  sex TEXT NOT NULL,
  heightCm INTEGER NOT NULL,
  weightKg REAL NOT NULL,
  goal TEXT NOT NULL,
  experienceLevel INTEGER NOT NULL,
  budgetLevel INTEGER NOT NULL DEFAULT 2
);
CREATE TABLE IF NOT EXISTS ProfileEquipment(
  profileId TEXT NOT NULL,
  item TEXT NOT NULL,
  PRIMARY KEY(profileId, item)
);
CREATE TABLE IF NOT EXISTS ProfileDietPref(
  profileId TEXT NOT NULL,
  pref TEXT NOT NULL,
  PRIMARY KEY(profileId, pref)
);
CREATE TABLE IF NOT EXISTS ProfileAllergy(
  profileId TEXT NOT NULL,
  allergy TEXT NOT NULL,
  PRIMARY KEY(profileId, allergy)
);
CREATE TABLE IF NOT EXISTS ProfileSchedule(
  profileId TEXT NOT NULL,
  day TEXT NOT NULL, -- Mon..Sun
  active INTEGER NOT NULL, -- 0/1
  PRIMARY KEY(profileId, day)
);
CREATE TABLE Workout(
  id TEXT NOT NULL PRIMARY KEY,
  weekIndex INTEGER NOT NULL,
  date TEXT NOT NULL -- ISO-8601 LocalDate
);
CREATE TABLE WorkoutSet(
  workoutId TEXT NOT NULL REFERENCES Workout(id) ON DELETE CASCADE,
  exerciseId TEXT NOT NULL,
  reps INTEGER NOT NULL,
  weightKg REAL,
  rpe REAL
);
CREATE INDEX IF NOT EXISTS idx_workout_week ON Workout(weekIndex);
CREATE INDEX IF NOT EXISTS idx_set_workout ON WorkoutSet(workoutId);
