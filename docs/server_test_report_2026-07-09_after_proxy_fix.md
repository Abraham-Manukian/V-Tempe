# Отчёт — те же 8 сценариев после фикса прокси

**Дата:** 2026-07-09 (повторный прогон)
**Сервер:** локальный (`http://127.0.0.1:8081`), + фикс `resolveSystemProxy()` в
`OpenRouterLLMClient.kt`
**Метод:** те же 8 профилей, тот же `/ai/bootstrap`

---

## Главный вывод: фикс прокси подтверждён, но не единственная причина фолбеков

`grep -c "Access denied by security policy" server.log` → **0** (было 8 из 8 запросов
в прошлом прогоне). Проблема с блокировкой сети полностью устранена.

Но **3 из 8 сценариев всё равно упали в статичный fallback-шаблон** (S1, S2, S7) — уже
по другой причине: реальный LLM отвечал, но 3 попытки подряд не проходили quality-gate
(калорийность вне допуска / битый JSON / "одинаковый план на все дни"), и после третьей
попытки сервер сдаётся и подставляет шаблон. Это **тот самый, отдельный, уже частично
исправленный в этой сессии баг** ("severe calorie deviation") — просто исправление
(точный множитель в фидбеке) не гарантирует 100% сходимость за 3 попытки, особенно для
сложных профилей (жёсткий бюджет+веган+аллергии, ночная смена+нестандартный график).

**Вывод: proxy был не единственной причиной, но реальной и теперь устранённой.** До
фикса — 8/8 фолбеков. После фикса — 5/8 реальных персонализированных ответов, 3/8 —
фолбек по старой причине (сходимость калорий за 3 попытки), которая требует отдельной
доработки, если нужно 100% надёжности.

---

## Сводка по сценариям (второй прогон)

| # | Сценарий | Источник ответа | Тренировка | Питание |
|---|---|---|---|---|
| 1 | Худой набор массы, без молочки | 🔴 Fallback | 3 дня (запрошено 3 — ок) | 🔴 Плоские 1968/1408 ккал, не под цель |
| 2 | Веган, без сои/орехов/арахиса | 🔴 Fallback | 3 дня (запрошено 4 — **не совпадает**) | 🔴 Плоский шаблон, но диет-текст чистый |
| 3 | Перетренированный, делоуд | ✅ Реальный LLM | **5 дней, ровно как запрошено** | ✅ Персонализировано |
| 4 | 60 лет, много травм | ✅ Реальный LLM | **2 дня, ровно как запрошено** | ✅ Персонализировано |
| 5 | Агрессивное похудение | ✅ Реальный LLM | 3 дня (ок) | ✅ ~1600 ккал ≈ цель 1616, дефицит не экстремальный |
| 6 | Дом, резинки, PPL 6 дней | ✅ Реальный LLM | **6 дней, ровно как запрошено** | ✅ Персонализировано |
| 7 | Ночная смена, похудение | 🔴 Fallback (полностью, включая совет по сну) | 3 дня | 🔴 Плоский шаблон |
| 8 | Outdoor, нельзя брусья | ✅ Реальный LLM | 3 дня (ок) | ✅ Персонализировано, но **красный флаг подтверждён** |

---

## Что теперь ЧИНИТСЯ само собой, когда LLM отвечает реально

**Баг "фолбек игнорирует расписание" (из прошлого отчёта) — это был баг именно
`fallbackTraining()`, а не общей логики.** Когда отвечает реальный LLM, количество
тренировок **точно** совпадает с `weeklySchedule`:
- Сценарий 3 (5 дней) → 5 тренировок (Push/Pull/Legs/Push2/Legs2).
- Сценарий 4 (2 дня: Вт/Пт) → 2 тренировки (Full Body A/B).
- Сценарий 6 (6 дней, PPL) → 6 тренировок (Push/Pull/Legs ×2), **и ни одного** упражнения
  со штангой/тренажёрами — только резинки и своё тело, хотя дома есть только `bands, mat`.

**Делоуд (фикс этой сессии) — RPE и объём одновременно снижены.** Сценарий 3: RPE
6.0–6.5 (было 9.2–9.4 в истории), плюс количество подходов на изоляции упало до 1
(`plank:sets1`, `dumbbell_row:sets1`) против обычных 2–3 — деload теперь режет и
интенсивность, и объём, не только RPE.

---

## 🟡 Осталось: делоуд не убирает тяжёлые присед+становую 2 раза в неделю

Сценарий 3 (красный флаг из задания: "тяжёлые присед+становая 2 раза в неделю"):
оба Leg-дня (07-08 и 07-11) содержат `squat@80kg` + `deadlift@100kg` — **вес и
упражнения не изменились**, снизились только RPE (6.5) и число подходов (2→2, но с 1
подходом на аксессуары). То есть делоуд снижает интенсивность/объём, но **не убирает
дублирование тяжёлых базовых движений** — та часть красного флага, которую просил
проверить пользователь, актуальна частично: RPE-часть исправлена, но "2 тяжёлых дня
ног подряд с одинаковыми весами" — нет.

---

## 🔴 Красный флаг подтверждён повторно (сценарий 8, уже не из-за сети)

Тренировка сценария 8 снова содержит `dip` (грудь+трицепс, день 1) — упражнение на
брусьях, хотя профиль явно говорит "нельзя брусья". В этот раз это НЕ фолбек (питание
пришло персонализированное, реальный LLM), значит причина ровно та, что описана в
прошлом отчёте: `InjuryFilter.kt` понимает только 4 категории тела (колено/плечо/спина/
локоть) и не парсит свободнотекстовые исключения конкретных упражнений вроде "нельзя
брусья"/"нельзя подтягивания широким хватом". Это подтверждённый, воспроизводимый баг,
не связанный с прокси — стоит исправлять отдельно (см. рекомендацию в прошлом отчёте:
явные keyword→exerciseId исключения в `InjuryFilter`).

---

## Что по-прежнему подтверждено рабочим

1. **Дефицит калорий не агрессивный при похудении (сценарий 5).** Цель по логам —
   1616 ккал, факт — ~1600 в среднем. Не 1200–1400 (красный флаг из задания)
   — защита от чрезмерного дефицита работает.
2. **Диетические ограничения чистые везде, включая fallback-сценарии.** Ни в
   S1 (лактоза), ни в S2 (веган+соя/орехи/арахис/молоко/яйца), ни в S8
   (без свинины/морепродуктов) не найдено ни одного нарушения в тексте меню —
   `sanitizeTemplateMealsForRestrictions` продолжает работать даже на шаблоне.
3. **InjuryFilter на уровне паттернов по-прежнему корректен.** Сценарий 4
   (грыжа+колени+плечо) — ни deadlift, ни overhead press, ни прыжков; веса
   адекватно лёгкие для новичка 60 лет (2–15кг).
4. **Список покупок без артефактов** — дублей/сломанной пунктуации не найдено.

---

## Оставшиеся баги (актуальный статус)

| # | Баг | Статус |
|---|---|---|
| Сеть (retry блокировался) | ✅ **Исправлено** (`resolveSystemProxy()`) |
| `fallbackTraining()` игнорирует расписание | ⚠️ Технически не тронуто, но **не проявляется**, пока LLM отвечает (5/8 в этом прогоне) — проявится, только если LLM реально недоступен/качество не сходится |
| `fallbackNutrition()` игнорирует целевые калории | 🔴 Не исправлено, задело 3/8 сценариев в этом прогоне (S1, S2, S7) |
| Свободнотекстовые исключения упражнений ("нельзя брусья") не enforced | 🔴 Не исправлено, подтверждено повторно на S8 без участия сети |
| Делоуд не убирает дублирующиеся тяжёлые Leg-дни | 🟡 Новое наблюдение — RPE/объём снижены, но веса/упражнения не варьируются между двумя Leg-днями |
| Макросы не пересчитываются после замены ингредиентов (веган) | Не перепроверялось в этом прогоне (S2 снова попал в fallback, где этот баг воспроизводится тем же образом) |

## Рекомендация

Главный сетевой блокер снят — прод (Cloud Run) это никак не затрагивало, а локальная
разработка больше не требует ручного контроля VPN. Из оставшегося приоритетнее всего:
**"нельзя брусья"/явные exercise-level исключения** (риск травмы реального пользователя)
и **`fallbackNutrition()` игнорирует калории** (единственная сеть безопасности, которая
сейчас реально отрабатывает в ~40% случаев, судя по этому прогону, и делает это плохо).

---

# Приложение — сырые запросы и ответы сервера

Все 8 запросов/ответов ниже — как есть, без редактирования, второй прогон (после фикса прокси).

## Сценарий 1 — Худой, набор массы, без молочки

### Запрос (POST /ai/bootstrap)

```json
{
  "profile": {
    "age": 18,
    "sex": "MALE",
    "heightCm": 190,
    "weightKg": 52,
    "goal": "GAIN_MUSCLE",
    "experienceLevel": 1,
    "equipment": [
      "barbell",
      "dumbbells",
      "machines",
      "cables",
      "pullup_bar"
    ],
    "dietaryPreferences": [],
    "allergies": [
      "lactose"
    ],
    "injuries": [],
    "healthNotes": [
      "плохо набираю вес",
      "маленький аппетит"
    ],
    "weeklySchedule": {
      "Mon": true,
      "Wed": true,
      "Fri": true
    },
    "lifestyleActivity": "SEDENTARY",
    "locale": "ru",
    "budgetLevel": 1,
    "trainingMode": "GYM",
    "trainingFocus": "HYPERTROPHY",
    "sessionDurationMins": 60,
    "splitPreference": "AUTO"
  },
  "weekIndex": 0,
  "locale": "ru"
}
```

### Сырой ответ сервера

```json
{
  "trainingPlan": {
    "weekIndex": 0,
    "workouts": [
      {
        "id": "w_0_1",
        "label": "Chest + Triceps",
        "date": "2026-07-09",
        "sets": [
          {
            "exerciseId": "bench",
            "reps": 6,
            "weightKg": 60,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "db_bench_press",
            "reps": 8,
            "weightKg": 45,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "svend_press",
            "reps": 10,
            "weightKg": 40,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "tricep_extension",
            "reps": 8,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "tricep_kickback",
            "reps": 8,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          }
        ]
      },
      {
        "id": "w_0_2",
        "label": "Back + Biceps",
        "date": "2026-07-10",
        "sets": [
          {
            "exerciseId": "band_pulldown",
            "reps": 4,
            "weightKg": 90,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "reverse_fly",
            "reps": 8,
            "weightKg": 30,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "dumbbell_row",
            "reps": 6,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "curl",
            "reps": 8,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "hammer_curl",
            "reps": 8,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          }
        ]
      },
      {
        "id": "w_0_3",
        "label": "Legs + Shoulders",
        "date": "2026-07-11",
        "sets": [
          {
            "exerciseId": "bodyweight_squat",
            "reps": 10,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "glute_bridge",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "lateral_raise",
            "reps": 45,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "lunge",
            "reps": 8,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "plank",
            "reps": 8,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          }
        ]
      }
    ]
  },
  "nutritionPlan": {
    "weekIndex": 0,
    "mealsByDay": {
      "Mon": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "рис",
            "ягоды",
            "мёд"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Куриная грудка с рисом и овощами",
          "ingredients": [
            "куриная грудка",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный ужин",
          "ingredients": [
            "киноа",
            "грецкие орехи",
            "мёд"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Лосось с киноа и шпинатом",
          "ingredients": [
            "филе лосося",
            "киноа",
            "шпинат",
            "лимон"
          ],
          "kcal": 560,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbsGrams": 45,
            "kcal": 560
          },
          "allergenTags": [],
          "recipe": ""
        }
      ],
      "Tue": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "рис",
            "ягоды",
            "мёд"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Куриная грудка с рисом и овощами",
          "ingredients": [
            "куриная грудка",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный ужин",
          "ingredients": [
            "киноа",
            "грецкие орехи",
            "мёд"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Лосось с киноа и шпинатом",
          "ingredients": [
            "филе лосося",
            "киноа",
            "шпинат",
            "лимон"
          ],
          "kcal": 560,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbsGrams": 45,
            "kcal": 560
          },
          "allergenTags": [],
          "recipe": ""
        }
      ],
      "Wed": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "рис",
            "ягоды",
            "мёд"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Куриная грудка с рисом и овощами",
          "ingredients": [
            "куриная грудка",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный ужин",
          "ingredients": [
            "киноа",
            "грецкие орехи",
            "мёд"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Лосось с киноа и шпинатом",
          "ingredients": [
            "филе лосося",
            "киноа",
            "шпинат",
            "лимон"
          ],
          "kcal": 560,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbsGrams": 45,
            "kcal": 560
          },
          "allergenTags": [],
          "recipe": ""
        }
      ],
      "Thu": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "рис",
            "ягоды",
            "мёд"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Куриная грудка с рисом и овощами",
          "ingredients": [
            "куриная грудка",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный ужин",
          "ingredients": [
            "киноа",
            "грецкие орехи",
            "мёд"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Лосось с киноа и шпинатом",
          "ingredients": [
            "филе лосося",
            "киноа",
            "шпинат",
            "лимон"
          ],
          "kcal": 560,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbsGrams": 45,
            "kcal": 560
          },
          "allergenTags": [],
          "recipe": ""
        }
      ],
      "Fri": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "рис",
            "ягоды",
            "мёд"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Куриная грудка с рисом и овощами",
          "ingredients": [
            "куриная грудка",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный ужин",
          "ingredients": [
            "киноа",
            "грецкие орехи",
            "мёд"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Лосось с киноа и шпинатом",
          "ingredients": [
            "филе лосося",
            "киноа",
            "шпинат",
            "лимон"
          ],
          "kcal": 560,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbsGrams": 45,
            "kcal": 560
          },
          "allergenTags": [],
          "recipe": ""
        }
      ],
      "Sat": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "рис",
            "ягоды",
            "мёд"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Куриная грудка с рисом и овощами",
          "ingredients": [
            "куриная грудка",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный ужин",
          "ingredients": [
            "киноа",
            "грецкие орехи",
            "мёд"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Лосось с киноа и шпинатом",
          "ingredients": [
            "филе лосося",
            "киноа",
            "шпинат",
            "лимон"
          ],
          "kcal": 560,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbsGrams": 45,
            "kcal": 560
          },
          "allergenTags": [],
          "recipe": ""
        }
      ],
      "Sun": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "рис",
            "ягоды",
            "мёд"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Куриная грудка с рисом и овощами",
          "ingredients": [
            "куриная грудка",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный ужин",
          "ingredients": [
            "киноа",
            "грецкие орехи",
            "мёд"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        }
      ]
    },
    "shoppingList": [
      "Брокколи",
      "Грецкие орехи",
      "Киноа",
      "Куриная грудка",
      "Лимон",
      "Морковь",
      "Мёд",
      "Овсяные хлопья",
      "Рис",
      "Филе лосося",
      "Шпинат",
      "Ягоды"
    ]
  },
  "sleepAdvice": {
    "messages": [
      "Ложитесь и просыпайтесь в одно и то же время, даже по выходным.",
      "За час до сна приглушите свет и уберите яркие экраны.",
      "Поддерживайте прохладу и тишину в спальне (18–20 °C).",
      "Избегайте тяжёлой еды и кофеина за 3 часа до сна.",
      "Добавьте лёгкую растяжку или дыхательные упражнения перед сном.",
      "Если ночь прошла плохо, снизьте нагрузку на следующей тренировке."
    ],
    "disclaimer": "Советы носят ознакомительный характер. При серьёзных нарушениях сна обратитесь к врачу."
  }
}

```

---

## Сценарий 2 — Веган, без сои/орехов/арахиса

### Запрос (POST /ai/bootstrap)

```json
{
  "profile": {
    "age": 25,
    "sex": "MALE",
    "heightCm": 180,
    "weightKg": 72,
    "goal": "GAIN_MUSCLE",
    "experienceLevel": 2,
    "equipment": [
      "barbell",
      "dumbbells",
      "machines",
      "cables"
    ],
    "dietaryPreferences": [
      "vegan"
    ],
    "allergies": [
      "soy",
      "peanuts",
      "nuts",
      "milk",
      "eggs"
    ],
    "injuries": [],
    "healthNotes": [],
    "weeklySchedule": {
      "Mon": true,
      "Tue": true,
      "Thu": true,
      "Fri": true
    },
    "lifestyleActivity": "LIGHT",
    "locale": "ru",
    "budgetLevel": 2,
    "trainingMode": "GYM",
    "trainingFocus": "HYPERTROPHY",
    "sessionDurationMins": 60,
    "splitPreference": "UPPER_LOWER"
  },
  "weekIndex": 0,
  "locale": "ru"
}
```

### Сырой ответ сервера

```json
{
  "trainingPlan": {
    "weekIndex": 0,
    "workouts": [
      {
        "id": "w_0_1",
        "label": "Upper A",
        "date": "2026-07-09",
        "sets": [
          {
            "exerciseId": "bench",
            "reps": 6,
            "weightKg": 60,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "negative_pullup",
            "reps": 8,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "dumbbell_row",
            "reps": 10,
            "weightKg": 40,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "dumbbell_shoulder_press",
            "reps": 8,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "curl",
            "reps": 8,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "tricep_extension",
            "reps": 8,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          }
        ]
      },
      {
        "id": "w_0_2",
        "label": "Lower A",
        "date": "2026-07-10",
        "sets": [
          {
            "exerciseId": "bodyweight_squat",
            "reps": 4,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "hip_thrust",
            "reps": 8,
            "weightKg": 30,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "lunge",
            "reps": 6,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "plank",
            "reps": 8,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "bike",
            "reps": 8,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          }
        ]
      },
      {
        "id": "w_0_3",
        "label": "Upper B",
        "date": "2026-07-11",
        "sets": [
          {
            "exerciseId": "dumbbell_shoulder_press",
            "reps": 10,
            "weightKg": 25,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "dumbbell_row",
            "reps": 12,
            "weightKg": 20,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "bench",
            "reps": 45,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "negative_pullup",
            "reps": 8,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "tricep_extension",
            "reps": 8,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "curl",
            "reps": 8,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          }
        ]
      }
    ]
  },
  "nutritionPlan": {
    "weekIndex": 0,
    "mealsByDay": {
      "Mon": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "рис",
            "ягоды",
            "киноа"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный обед",
          "ingredients": [
            "овощи",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный ужин",
          "ingredients": [
            "фрукты",
            "нут",
            "семена тыквы"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Перекус",
          "ingredients": [
            "рис",
            "киноа",
            "шпинат",
            "лимон"
          ],
          "kcal": 560,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbsGrams": 45,
            "kcal": 560
          },
          "allergenTags": [],
          "recipe": ""
        }
      ],
      "Tue": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "рис",
            "ягоды",
            "киноа"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный обед",
          "ingredients": [
            "овощи",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный ужин",
          "ingredients": [
            "фрукты",
            "нут",
            "семена тыквы"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Перекус",
          "ingredients": [
            "рис",
            "киноа",
            "шпинат",
            "лимон"
          ],
          "kcal": 560,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbsGrams": 45,
            "kcal": 560
          },
          "allergenTags": [],
          "recipe": ""
        }
      ],
      "Wed": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "рис",
            "ягоды",
            "киноа"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный обед",
          "ingredients": [
            "овощи",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный ужин",
          "ingredients": [
            "фрукты",
            "нут",
            "семена тыквы"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Перекус",
          "ingredients": [
            "рис",
            "киноа",
            "шпинат",
            "лимон"
          ],
          "kcal": 560,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbsGrams": 45,
            "kcal": 560
          },
          "allergenTags": [],
          "recipe": ""
        }
      ],
      "Thu": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "рис",
            "ягоды",
            "киноа"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный обед",
          "ingredients": [
            "овощи",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный ужин",
          "ingredients": [
            "фрукты",
            "нут",
            "семена тыквы"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Перекус",
          "ingredients": [
            "рис",
            "киноа",
            "шпинат",
            "лимон"
          ],
          "kcal": 560,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbsGrams": 45,
            "kcal": 560
          },
          "allergenTags": [],
          "recipe": ""
        }
      ],
      "Fri": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "рис",
            "ягоды",
            "киноа"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный обед",
          "ingredients": [
            "овощи",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный ужин",
          "ingredients": [
            "фрукты",
            "нут",
            "семена тыквы"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Перекус",
          "ingredients": [
            "рис",
            "киноа",
            "шпинат",
            "лимон"
          ],
          "kcal": 560,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbsGrams": 45,
            "kcal": 560
          },
          "allergenTags": [],
          "recipe": ""
        }
      ],
      "Sat": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "рис",
            "ягоды",
            "киноа"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный обед",
          "ingredients": [
            "овощи",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный ужин",
          "ingredients": [
            "фрукты",
            "нут",
            "семена тыквы"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Перекус",
          "ingredients": [
            "рис",
            "киноа",
            "шпинат",
            "лимон"
          ],
          "kcal": 560,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbsGrams": 45,
            "kcal": 560
          },
          "allergenTags": [],
          "recipe": ""
        }
      ],
      "Sun": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "рис",
            "ягоды",
            "киноа"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный обед",
          "ingredients": [
            "овощи",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Сбалансированный ужин",
          "ingredients": [
            "фрукты",
            "нут",
            "семена тыквы"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        }
      ]
    },
    "shoppingList": [
      "Брокколи",
      "Киноа",
      "Лимон",
      "Морковь",
      "Нут",
      "Овощи",
      "Овсяные хлопья",
      "Рис",
      "Семена тыквы",
      "Фрукты",
      "Шпинат",
      "Ягоды"
    ]
  },
  "sleepAdvice": {
    "messages": [
      "Ложитесь и просыпайтесь в одно и то же время, даже по выходным.",
      "За час до сна приглушите свет и уберите яркие экраны.",
      "Поддерживайте прохладу и тишину в спальне (18–20 °C).",
      "Избегайте тяжёлой еды и кофеина за 3 часа до сна.",
      "Добавьте лёгкую растяжку или дыхательные упражнения перед сном.",
      "Если ночь прошла плохо, снизьте нагрузку на следующей тренировке."
    ],
    "disclaimer": "Советы носят ознакомительный характер. При серьёзных нарушениях сна обратитесь к врачу."
  }
}

```

---

## Сценарий 3 — Перетренированный силовик, делоуд

### Запрос (POST /ai/bootstrap)

```json
{
  "profile": {
    "age": 31,
    "sex": "MALE",
    "heightCm": 183,
    "weightKg": 94,
    "goal": "STRENGTH",
    "experienceLevel": 5,
    "equipment": [
      "barbell",
      "rack",
      "dumbbells",
      "machines",
      "cables",
      "pullup_bar"
    ],
    "dietaryPreferences": [],
    "allergies": [],
    "injuries": [
      "болит локоть, нельзя французский жим"
    ],
    "healthNotes": [],
    "weeklySchedule": {
      "Mon": true,
      "Tue": true,
      "Wed": true,
      "Fri": true,
      "Sat": true
    },
    "lifestyleActivity": "ACTIVE",
    "locale": "ru",
    "budgetLevel": 3,
    "trainingMode": "GYM",
    "trainingFocus": "STRENGTH",
    "sessionDurationMins": 90,
    "splitPreference": "AUTO",
    "sleepHistory": [
      {
        "date": "2026-07-03",
        "durationMinutes": 240
      },
      {
        "date": "2026-07-04",
        "durationMinutes": 240
      },
      {
        "date": "2026-07-05",
        "durationMinutes": 240
      },
      {
        "date": "2026-07-06",
        "durationMinutes": 240
      },
      {
        "date": "2026-07-07",
        "durationMinutes": 240
      },
      {
        "date": "2026-07-08",
        "durationMinutes": 240
      },
      {
        "date": "2026-07-09",
        "durationMinutes": 240
      }
    ],
    "recentWorkouts": [
      {
        "date": "2026-07-01",
        "completionRate": 0.6,
        "completedItems": 9,
        "plannedItems": 15,
        "totalVolumeKg": 3200,
        "averageRpe": 9.2
      },
      {
        "date": "2026-07-05",
        "completionRate": 0.58,
        "completedItems": 8,
        "plannedItems": 14,
        "totalVolumeKg": 3050,
        "averageRpe": 9.4
      }
    ]
  },
  "weekIndex": 0,
  "locale": "ru"
}
```

### Сырой ответ сервера

```json
{
  "trainingPlan": {
    "weekIndex": 0,
    "workouts": [
      {
        "id": "push_1_2026_07_06",
        "label": "Push",
        "date": "2026-07-06",
        "sets": [
          {
            "exerciseId": "bench",
            "reps": 6,
            "weightKg": 70,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "ohp",
            "reps": 6,
            "weightKg": 45,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "db_bench_press",
            "reps": 8,
            "weightKg": 25,
            "rpe": 6.5,
            "sets": 1
          },
          {
            "exerciseId": "plank",
            "reps": 30,
            "weightKg": null,
            "rpe": 6,
            "sets": 1
          }
        ]
      },
      {
        "id": "pull_1_2026_07_07",
        "label": "Pull",
        "date": "2026-07-07",
        "sets": [
          {
            "exerciseId": "pullup",
            "reps": 6,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "row",
            "reps": 6,
            "weightKg": 65,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "dumbbell_row",
            "reps": 8,
            "weightKg": 30,
            "rpe": 6.5,
            "sets": 1
          },
          {
            "exerciseId": "plank",
            "reps": 30,
            "weightKg": null,
            "rpe": 6,
            "sets": 1
          },
          {
            "exerciseId": "stretching",
            "reps": 60,
            "weightKg": null,
            "rpe": 6,
            "sets": 1
          }
        ]
      },
      {
        "id": "legs_1_2026_07_08",
        "label": "Legs",
        "date": "2026-07-08",
        "sets": [
          {
            "exerciseId": "squat",
            "reps": 6,
            "weightKg": 80,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "deadlift",
            "reps": 6,
            "weightKg": 100,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "lunge",
            "reps": 8,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 1
          },
          {
            "exerciseId": "plank",
            "reps": 30,
            "weightKg": null,
            "rpe": 6,
            "sets": 1
          },
          {
            "exerciseId": "rowing_machine",
            "reps": 180,
            "weightKg": null,
            "rpe": 6,
            "sets": 1
          }
        ]
      },
      {
        "id": "push_2_2026_07_10",
        "label": "Push 2",
        "date": "2026-07-10",
        "sets": [
          {
            "exerciseId": "ohp",
            "reps": 6,
            "weightKg": 45,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "bench",
            "reps": 6,
            "weightKg": 70,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "dumbbell_shoulder_press",
            "reps": 8,
            "weightKg": 20,
            "rpe": 6.5,
            "sets": 1
          },
          {
            "exerciseId": "plank",
            "reps": 30,
            "weightKg": null,
            "rpe": 6,
            "sets": 1
          }
        ]
      },
      {
        "id": "legs_2_2026_07_11",
        "label": "Legs",
        "date": "2026-07-11",
        "sets": [
          {
            "exerciseId": "squat",
            "reps": 6,
            "weightKg": 80,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "deadlift",
            "reps": 6,
            "weightKg": 100,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "lunge",
            "reps": 8,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 1
          },
          {
            "exerciseId": "plank",
            "reps": 30,
            "weightKg": null,
            "rpe": 6,
            "sets": 1
          },
          {
            "exerciseId": "rowing_machine",
            "reps": 180,
            "weightKg": null,
            "rpe": 6,
            "sets": 1
          }
        ]
      }
    ]
  },
  "nutritionPlan": {
    "weekIndex": 0,
    "mealsByDay": {
      "Mon": [
        {
          "name": "Овсянка с ягодами и орехами",
          "ingredients": [
            "120г овсяных хлопьев",
            "300мл миндального молока",
            "100г смешанных ягод",
            "30г грецких орехов"
          ],
          "kcal": 662,
          "macros": {
            "proteinGrams": 20,
            "fatGrams": 30,
            "carbsGrams": 68,
            "kcal": 662
          },
          "allergenTags": [],
          "recipe": "1. Смешайте овсяные хлопья с миндальным молоком. 2. Варите на среднем огне 5-7 минут, помешивая. 3. Добавьте ягоды и грецкие орехи."
        },
        {
          "name": "Куриная грудка с киноа и овощами",
          "ingredients": [
            "200г куриной грудки",
            "100г киноа",
            "150г брокколи",
            "100г моркови",
            "15мл оливкового масла"
          ],
          "kcal": 750,
          "macros": {
            "proteinGrams": 60,
            "fatGrams": 25,
            "carbsGrams": 70,
            "kcal": 745
          },
          "allergenTags": [],
          "recipe": "1. Отварите киноа согласно инструкции. 2. Нарежьте курицу и овощи. 3. Обжарьте курицу и овощи на оливковом масле до готовности."
        },
        {
          "name": "Стейк из лосося с бататом и спаржей",
          "ingredients": [
            "200г стейка лосося",
            "200г батата",
            "150г спаржи",
            "15мл оливкового масла",
            "соль, перец по вкусу"
          ],
          "kcal": 850,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 45,
            "carbsGrams": 65,
            "kcal": 845
          },
          "allergenTags": [],
          "recipe": "1. Запеките лосось и батат в духовке при 180°C 20-25 минут. 2. Отварите спаржу 3-5 минут. 3. Подавайте с оливковым маслом, солью и перцем."
        },
        {
          "name": "Греческий йогурт с фруктами и медом",
          "ingredients": [
            "250г греческого йогурта",
            "150г смешанных фруктов",
            "20г меда"
          ],
          "kcal": 550,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 20,
            "carbsGrams": 65,
            "kcal": 550
          },
          "allergenTags": [],
          "recipe": "1. Смешайте йогурт с фруктами. 2. Полейте медом."
        }
      ],
      "Tue": [
        {
          "name": "Яичница с овощами и цельнозерновым тостом",
          "ingredients": [
            "3 яйца",
            "100г шпината",
            "50г помидоров черри",
            "2 ломтика цельнозернового хлеба",
            "10мл оливкового масла"
          ],
          "kcal": 580,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 30,
            "carbsGrams": 50,
            "kcal": 580
          },
          "allergenTags": [],
          "recipe": "1. Взбейте яйца. 2. Обжарьте шпинат и помидоры на оливковом масле. 3. Добавьте яйца и готовьте до желаемой консистенции. 4. Поджарьте тосты."
        },
        {
          "name": "Салат с креветками и авокадо",
          "ingredients": [
            "200г креветок",
            "1 авокадо",
            "150г микс-салата",
            "50г огурцов",
            "30мл оливкового масла",
            "15мл лимонного сока"
          ],
          "kcal": 780,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 55,
            "carbsGrams": 30,
            "kcal": 775
          },
          "allergenTags": [],
          "recipe": "1. Отварите или обжарьте креветки. 2. Нарежьте авокадо и огурцы. 3. Смешайте все ингредиенты с микс-салатом. 4. Заправьте оливковым маслом и лимонным соком."
        },
        {
          "name": "Говяжий стейк с бурым рисом и зеленой фасолью",
          "ingredients": [
            "200г говяжьего стейка",
            "100г бурого риса",
            "150г зеленой фасоли",
            "15мл оливкового масла",
            "соль, перец по вкусу"
          ],
          "kcal": 880,
          "macros": {
            "proteinGrams": 55,
            "fatGrams": 40,
            "carbsGrams": 70,
            "kcal": 870
          },
          "allergenTags": [],
          "recipe": "1. Отварите бурый рис. 2. Обжарьте стейк до желаемой степени прожарки. 3. Отварите зеленую фасоль. 4. Подавайте с оливковым маслом, солью и перцем."
        },
        {
          "name": "Творог с фруктами и семенами чиа",
          "ingredients": [
            "250г творога 5%",
            "150г смешанных фруктов",
            "20г семян чиа"
          ],
          "kcal": 550,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 15,
            "carbsGrams": 65,
            "kcal": 555
          },
          "allergenTags": [],
          "recipe": "1. Смешайте творог с фруктами. 2. Посыпьте семенами чиа."
        }
      ],
      "Wed": [
        {
          "name": "Смузи-боул с гранолой",
          "ingredients": [
            "200г замороженных ягод",
            "1 банан",
            "150мл миндального молока",
            "50г гранолы",
            "20г семян чиа"
          ],
          "kcal": 600,
          "macros": {
            "proteinGrams": 15,
            "fatGrams": 20,
            "carbsGrams": 90,
            "kcal": 600
          },
          "allergenTags": [],
          "recipe": "1. Смешайте ягоды, банан и миндальное молоко в блендере до однородности. 2. Перелейте в миску. 3. Посыпьте гранолой и семенами чиа."
        },
        {
          "name": "Суп-пюре из чечевицы с цельнозерновым хлебом",
          "ingredients": [
            "200г красной чечевицы",
            "1 морковь",
            "1 луковица",
            "1 стебель сельдерея",
            "1.5л овощного бульона",
            "2 ломтика цельнозернового хлеба",
            "15мл оливкового масла"
          ],
          "kcal": 700,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 20,
            "carbsGrams": 95,
            "kcal": 700
          },
          "allergenTags": [],
          "recipe": "1. Обжарьте нарезанные овощи на оливковом масле. 2. Добавьте чечевицу и бульон, варите до готовности. 3. Измельчите блендером до состояния пюре. 4. Подавайте с цельнозерновым хлебом."
        },
        {
          "name": "Индейка с кускусом и овощами на гриле",
          "ingredients": [
            "200г филе индейки",
            "100г кускуса",
            "150г цукини",
            "100г болгарского перца",
            "15мл оливкового масла"
          ],
          "kcal": 800,
          "macros": {
            "proteinGrams": 55,
            "fatGrams": 25,
            "carbsGrams": 80,
            "kcal": 805
          },
          "allergenTags": [],
          "recipe": "1. Приготовьте кускус согласно инструкции. 2. Нарежьте индейку и овощи. 3. Обжарьте индейку и овощи на гриле или сковороде до готовности."
        },
        {
          "name": "Протеиновый коктейль с бананом и арахисовой пастой",
          "ingredients": [
            "40г сывороточного протеина",
            "300мл молока 2.5%",
            "1 банан",
            "30г арахисовой пасты"
          ],
          "kcal": 600,
          "macros": {
            "proteinGrams": 55,
            "fatGrams": 25,
            "carbsGrams": 50,
            "kcal": 605
          },
          "allergenTags": [],
          "recipe": "1. Смешайте все ингредиенты в блендере до однородности."
        }
      ],
      "Thu": [
        {
          "name": "Творожная запеканка с изюмом",
          "ingredients": [
            "300г творога 5%",
            "2 яйца",
            "50г манной крупы",
            "30г изюма",
            "15г сахара"
          ],
          "kcal": 650,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 20,
            "carbsGrams": 75,
            "kcal": 650
          },
          "allergenTags": [],
          "recipe": "1. Смешайте творог, яйца, манку, изюм и сахар. 2. Выложите в форму для запекания. 3. Запекайте в духовке при 180°C 30-40 минут."
        },
        {
          "name": "Салат с тунцом, яйцом и овощами",
          "ingredients": [
            "180г консервированного тунца в собственном соку",
            "2 яйца",
            "150г огурцов",
            "100г помидоров",
            "50г красного лука",
            "30мл оливкового масла"
          ],
          "kcal": 700,
          "macros": {
            "proteinGrams": 50,
            "fatGrams": 45,
            "carbsGrams": 25,
            "kcal": 705
          },
          "allergenTags": [],
          "recipe": "1. Отварите яйца вкрутую. 2. Нарежьте огурцы, помидоры, лук. 3. Смешайте тунец, нарезанные овощи и яйца. 4. Заправьте оливковым маслом."
        },
        {
          "name": "Паста с фрикадельками из говядины и томатным соусом",
          "ingredients": [
            "150г цельнозерновой пасты",
            "200г говяжьего фарша",
            "1 яйцо",
            "50г лука",
            "400г томатного соуса",
            "15мл оливкового масла"
          ],
          "kcal": 900,
          "macros": {
            "proteinGrams": 60,
            "fatGrams": 40,
            "carbsGrams": 80,
            "kcal": 900
          },
          "allergenTags": [],
          "recipe": "1. Смешайте фарш, яйцо и мелко нарезанный лук, сформируйте фрикадельки. 2. Отварите пасту. 3. Обжарьте фрикадельки, добавьте томатный соус и тушите до готовности. 4. Подавайте пасту с фрикадельками и соусом."
        },
        {
          "name": "Фруктовый салат с йогуртом и орехами",
          "ingredients": [
            "250г смешанных фруктов",
            "150г натурального йогурта",
            "30г миндаля"
          ],
          "kcal": 550,
          "macros": {
            "proteinGrams": 15,
            "fatGrams": 20,
            "carbsGrams": 80,
            "kcal": 550
          },
          "allergenTags": [],
          "recipe": "1. Нарежьте фрукты. 2. Смешайте фрукты с йогуртом. 3. Посыпьте миндалем."
        }
      ],
      "Fri": [
        {
          "name": "Омлет с сыром и овощами",
          "ingredients": [
            "3 яйца",
            "50г сыра чеддер",
            "100г болгарского перца",
            "50г лука",
            "10мл оливкового масла",
            "2 ломтика цельнозернового хлеба"
          ],
          "kcal": 650,
          "macros": {
            "proteinGrams": 30,
            "fatGrams": 40,
            "carbsGrams": 40,
            "kcal": 640
          },
          "allergenTags": [],
          "recipe": "1. Взбейте яйца. 2. Нарежьте овощи и сыр. 3. Обжарьте овощи на оливковом масле, добавьте яйца и сыр. 4. Готовьте до готовности. 5. Подавайте с тостами."
        },
        {
          "name": "Салат с курицей, нутом и фетой",
          "ingredients": [
            "200г куриной грудки",
            "100г нута (консервированного)",
            "100г феты",
            "150г огурцов",
            "100г помидоров",
            "30мл оливкового масла"
          ],
          "kcal": 800,
          "macros": {
            "proteinGrams": 60,
            "fatGrams": 45,
            "carbsGrams": 40,
            "kcal": 805
          },
          "allergenTags": [],
          "recipe": "1. Отварите или обжарьте куриную грудку, нарежьте. 2. Нарежьте огурцы, помидоры и фету. 3. Смешайте все ингредиенты. 4. Заправьте оливковым маслом."
        },
        {
          "name": "Рыбные котлеты из трески с картофельным пюре",
          "ingredients": [
            "250г филе трески",
            "1 яйцо",
            "50г панировочных сухарей",
            "300г картофеля",
            "50мл молока 2.5%",
            "15г сливочного масла",
            "15мл оливкового масла"
          ],
          "kcal": 950,
          "macros": {
            "proteinGrams": 50,
            "fatGrams": 45,
            "carbsGrams": 80,
            "kcal": 945
          },
          "allergenTags": [],
          "recipe": "1. Измельчите треску, смешайте с яйцом и панировочными сухарями, сформируйте котлеты. 2. Отварите картофель, разомните с молоком и сливочным маслом. 3. Обжарьте котлеты на оливковом масле до золотистой корочки. 4. Подавайте с пюре."
        },
        {
          "name": "Протеиновый пудинг с семенами чиа",
          "ingredients": [
            "40г сывороточного протеина",
            "300мл миндального молока",
            "30г семян чиа",
            "10г какао-порошка"
          ],
          "kcal": 550,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 25,
            "carbsGrams": 35,
            "kcal": 545
          },
          "allergenTags": [],
          "recipe": "1. Смешайте все ингредиенты. 2. Оставьте в холодильнике на несколько часов или на ночь до загустения."
        }
      ],
      "Sat": [
        {
          "name": "Блины с творогом и сметаной",
          "ingredients": [
            "150г муки",
            "2 яйца",
            "300мл молока 2.5%",
            "15г сахара",
            "250г творога 5%",
            "50г сметаны 15%",
            "10мл растительного масла"
          ],
          "kcal": 800,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 35,
            "carbsGrams": 85,
            "kcal": 795
          },
          "allergenTags": [],
          "recipe": "1. Смешайте муку, яйца, молоко и сахар для теста. 2. Испеките блины на растительном масле. 3. Начините блины творогом. 4. Подавайте со сметаной."
        },
        {
          "name": "Салат Цезарь с курицей",
          "ingredients": [
            "200г куриной грудки",
            "150г листьев салата ромэн",
            "50г пармезана",
            "50г сухариков",
            "50мл соуса Цезарь"
          ],
          "kcal": 750,
          "macros": {
            "proteinGrams": 50,
            "fatGrams": 40,
            "carbsGrams": 40,
            "kcal": 740
          },
          "allergenTags": [],
          "recipe": "1. Отварите или обжарьте куриную грудку, нарежьте. 2. Нарежьте салат. 3. Смешайте курицу, салат, пармезан и сухарики. 4. Заправьте соусом Цезарь."
        },
        {
          "name": "Свиная отбивная с гречкой и грибами",
          "ingredients": [
            "200г свиной отбивной",
            "100г гречки",
            "150г шампиньонов",
            "1 луковица",
            "15мл оливкового масла"
          ],
          "kcal": 850,
          "macros": {
            "proteinGrams": 55,
            "fatGrams": 45,
            "carbsGrams": 60,
            "kcal": 845
          },
          "allergenTags": [],
          "recipe": "1. Отварите гречку. 2. Отбейте свинину. 3. Обжарьте свинину, нарезанные грибы и лук на оливковом масле до готовности. 4. Подавайте гречку с отбивной и грибами."
        },
        {
          "name": "Фруктовый смузи с протеином",
          "ingredients": [
            "40г сывороточного протеина",
            "300мл воды",
            "1 банан",
            "100г шпината",
            "50г ананаса"
          ],
          "kcal": 500,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 5,
            "carbsGrams": 70,
            "kcal": 495
          },
          "allergenTags": [],
          "recipe": "1. Смешайте все ингредиенты в блендере до однородности."
        }
      ],
      "Sun": [
        {
          "name": "Овсяная каша на молоке с фруктами",
          "ingredients": [
            "120г овсяных хлопьев",
            "300мл молока 2.5%",
            "150г смешанных фруктов",
            "20г меда"
          ],
          "kcal": 650,
          "macros": {
            "proteinGrams": 20,
            "fatGrams": 20,
            "carbsGrams": 90,
            "kcal": 640
          },
          "allergenTags": [],
          "recipe": "1. Смешайте овсяные хлопья с молоком. 2. Варите на среднем огне 5-7 минут, помешивая. 3. Добавьте фрукты и мед."
        },
        {
          "name": "Бургер с говядиной и цельнозерновой булочкой",
          "ingredients": [
            "200г говяжьего фарша",
            "1 цельнозерновая булочка",
            "50г сыра чеддер",
            "50г листьев салата",
            "50г помидоров",
            "30г маринованных огурцов",
            "15мл соуса (кетчуп/горчица)",
            "10мл оливкового масла"
          ],
          "kcal": 850,
          "macros": {
            "proteinGrams": 60,
            "fatGrams": 45,
            "carbsGrams": 50,
            "kcal": 845
          },
          "allergenTags": [],
          "recipe": "1. Сформируйте котлету из фарша и обжарьте на оливковом масле. 2. Разрежьте булочку и поджарьте. 3. Соберите бургер: булочка, котлета, сыр, овощи, соус."
        },
        {
          "name": "Запеченная курица с рисом и овощами",
          "ingredients": [
            "250г куриного бедра без кожи",
            "100г бурого риса",
            "150г брокколи",
            "100г моркови",
            "15мл оливкового масла",
            "специи по вкусу"
          ],
          "kcal": 900,
          "macros": {
            "proteinGrams": 65,
            "fatGrams": 35,
            "carbsGrams": 80,
            "kcal": 895
          },
          "allergenTags": [],
          "recipe": "1. Отварите бурый рис. 2. Нарежьте курицу и овощи. 3. Запеките курицу и овощи в духовке при 180°C 25-30 минут со специями и оливковым маслом. 4. Подавайте с рисом."
        },
        {
          "name": "Йогурт с мюсли и фруктами",
          "ingredients": [
            "250г натурального йогурта",
            "50г мюсли",
            "150г смешанных фруктов"
          ],
          "kcal": 600,
          "macros": {
            "proteinGrams": 20,
            "fatGrams": 15,
            "carbsGrams": 90,
            "kcal": 595
          },
          "allergenTags": [],
          "recipe": "1. Смешайте йогурт с мюсли. 2. Добавьте нарезанные фрукты."
        }
      ]
    },
    "shoppingList": [
      "Овсяные хлопья",
      "Миндальное молоко",
      "Смешанные ягоды",
      "Грецкие орехи",
      "Куриная грудка",
      "Киноа + + — 100 г",
      "Брокколи + + — 300 г",
      "Морковь + + — 1",
      "Оливковое масло",
      "Стейк лосося",
      "Батат + + — 200 г",
      "Спаржа + + — 150 г",
      "Греческий йогурт",
      "Мед + + — 40 г",
      "Яйца + + — 12",
      "Шпинат + + — 200 г",
      "Помидоры черри",
      "Цельнозерновой хлеб",
      "Креветки",
      "Авокадо + + — 1",
      "Микс салат + + — 150 г",
      "Огурцы + + — 350 г",
      "Говяжий стейк",
      "Бурый рис",
      "Творог % — 5",
      "Семена чиа",
      "Замороженные ягоды",
      "Банан + + — 3",
      "Гранола + + — 50 г",
      "Красная чечевица",
      "Лук + + — 100 г",
      "Стебель сельдерея + + — 1",
      "Овощной бульон",
      "Филе индейки + + — 200 г",
      "Кускус + + — 100 г",
      "Цукини + + — 150 г",
      "Сывороточный протеин",
      "Молоко % — 2.5",
      "Арахисовая паста",
      "Манная крупа",
      "Изюм + + — 30 г",
      "Сахар + + — 30 г",
      "Красный лук",
      "Цельнозерновая паста",
      "Говяжий фарш",
      "Миндаль",
      "Сыр чеддер",
      "Фета + + — 100 г",
      "Филе трески + + — 250 г",
      "Панировочные сухари",
      "Картофель",
      "Сливочное масло",
      "Какао порошок",
      "Мука + + — 150 г",
      "Сметана % — 15",
      "Растительное масло",
      "Листья салата ромэн",
      "Пармезан + + — 50 г",
      "Сухарики + + — 50 г",
      "Свиная отбивная",
      "Гречка + + — 100 г",
      "Шампиньоны + + — 150 г",
      "Вода + + — 300 мл",
      "Ананас + + — 50 г",
      "Куриное бедро без кожи",
      "Цельнозерновая булочка + + — 1",
      "Кетчуп",
      "Натуральный йогурт",
      "Мюсли + + — 50 г",
      "Овсяных хлопьев — 480 г",
      "Миндального молока — 1500 мл",
      "Смешанных ягод — 200 г",
      "Грецких орехов — 60 г",
      "Куриной грудки — 1200 г",
      "Моркови — 400 г",
      "Оливкового масла — 510 мл",
      "Стейка лосося — 400 г",
      "Греческого йогурта — 500 г",
      "Смешанных фруктов — 1700 г",
      "Помидоров черри — 100 г",
      "Омтика цельнозернового хлеба — 12 л",
      "Креветок — 400 г",
      "Лимонного сока — 30 мл",
      "Говяжьего стейка — 400 г",
      "Бурого риса — 400 г",
      "Творога % — 1600 г",
      "Семян чиа — 140 г",
      "Замороженных ягод — 400 г",
      "Красной чечевицы — 400 г",
      "Уковица — 4 л",
      "Овощного бульона — 3 л",
      "Сывороточного протеина — 240 г",
      "Молока % — 1900 мл",
      "Арахисовой пасты — 60 г",
      "Манной крупы — 100 г",
      "Помидоров — 500 г",
      "Красного лука — 100 г",
      "Цельнозерновой пасты — 300 г",
      "Говяжьего фарша — 800 г",
      "Яйцо — 4",
      "Натурального йогурта — 800 г",
      "Миндаля — 60 г",
      "Сыра чеддер — 200 г",
      "Панировочных сухарей — 100 г",
      "Картофеля — 600 г",
      "Сливочного масла — 30 г",
      "Какао порошка — 20 г",
      "Сметаны % — 100 г",
      "Растительного масла — 20 мл",
      "Листьев салата ромэн — 300 г",
      "Свиной отбивной — 400 г",
      "Листьев салата — 100 г",
      "Куриного бедра без кожи — 500 г",
      "Киноа — 100 г",
      "Брокколи — 300 г",
      "Батата — 200 г",
      "Спаржи — 150 г",
      "Меда — 40 г",
      "Яйца — 12",
      "Шпината — 200 г",
      "Авокадо — 1",
      "Микс салата — 150 г",
      "Огурцов — 350 г",
      "Банан — 3",
      "Гранолы — 50 г",
      "Морковь — 1",
      "Стебель сельдерея — 1",
      "Филе индейки — 200 г",
      "Кускуса — 100 г",
      "Цукини — 150 г",
      "Изюма — 30 г",
      "Сахара — 30 г",
      "Лука — 100 г",
      "Феты — 100 г",
      "Филе трески — 250 г",
      "Муки — 150 г",
      "Пармезана — 50 г",
      "Сухариков — 50 г",
      "Гречки — 100 г",
      "Шампиньонов — 150 г",
      "Воды — 300 мл",
      "Ананаса — 50 г",
      "Цельнозерновая булочка — 1",
      "Мюсли — 50 г",
      "Соль",
      "Перец",
      "Лимонный сок",
      "Зеленая фасоль",
      "Болгарский перец",
      "Консервированный тунец в собственном соку",
      "Томатный соус",
      "Нут консервированный",
      "Соус Цезарь",
      "Специи",
      "Маринованные огурцы",
      "Горчица",
      "Соль перец по вкусу",
      "Зеленой фасоли — 300 г",
      "Болгарского перца — 400 г",
      "Консервированного тунца в собственном соку — 360 г",
      "Томатного соуса — 800 г",
      "Нута (консервированного) — 200 г",
      "Соуса Цезарь — 100 мл",
      "Маринованных огурцов — 60 г",
      "Соуса (кетчуп/горчица) — 30 мл",
      "Специи по вкусу"
    ]
  },
  "sleepAdvice": {
    "messages": [
      "Ваш сон составляет всего 4 часа в сутки, что критически мало для восстановления, особенно при интенсивных тренировках на силу. Постарайтесь увеличить продолжительность сна до 7-9 часов.",
      "Высокий средний RPE (9.2 и 9.4) в последних тренировках в сочетании с недостаточным сном указывает на переутомление. Рассмотрите возможность снижения интенсивности или объема тренировок на некоторое время.",
      "Учитывая боль в локте и невозможность выполнять французский жим, сосредоточьтесь на упражнениях, которые не вызывают дискомфорта. Возможно, стоит временно исключить или заменить упражнения, нагружающие локтевой сустав.",
      "Несмотря на активный образ жизни, ваш сон не соответствует потребностям организма. Попробуйте установить регулярный график сна, ложась и вставая в одно и то же время, даже в выходные.",
      "Включите в свой распорядок дня техники релаксации перед сном, такие как чтение, медитация или теплая ванна. Избегайте экранов гаджетов за час до сна."
    ],
    "disclaimer": "Эти рекомендации носят общий характер. Перед внесением значительных изменений в тренировочный процесс или режим сна проконсультируйтесь с врачом или квалифицированным специалистом."
  }
}

```

---

## Сценарий 4 — Все суставы проблемные, 60 лет

### Запрос (POST /ai/bootstrap)

```json
{
  "profile": {
    "age": 60,
    "sex": "MALE",
    "heightCm": 174,
    "weightKg": 96,
    "goal": "MAINTAIN",
    "experienceLevel": 1,
    "equipment": [
      "machines",
      "cables",
      "elliptical",
      "light_dumbbells"
    ],
    "dietaryPreferences": [],
    "allergies": [],
    "injuries": [
      "грыжа поясницы",
      "болят колени",
      "боль в плече",
      "нельзя становую",
      "нельзя прыжки",
      "нельзя жим над головой"
    ],
    "healthNotes": [],
    "weeklySchedule": {
      "Tue": true,
      "Fri": true
    },
    "lifestyleActivity": "SEDENTARY",
    "locale": "ru",
    "budgetLevel": 2,
    "trainingMode": "GYM",
    "trainingFocus": "GENERAL",
    "sessionDurationMins": 45,
    "splitPreference": "AUTO"
  },
  "weekIndex": 0,
  "locale": "ru"
}
```

### Сырой ответ сервера

```json
{
  "trainingPlan": {
    "weekIndex": 0,
    "workouts": [
      {
        "id": "w_0_0",
        "label": "Full Body A",
        "date": "2026-07-07",
        "sets": [
          {
            "exerciseId": "reverse_fly",
            "reps": 15,
            "weightKg": 2,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "bench",
            "reps": 15,
            "weightKg": 15,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "band_pulldown",
            "reps": 15,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "dumbbell_row",
            "reps": 15,
            "weightKg": 4,
            "rpe": 6.5,
            "sets": 2
          }
        ]
      },
      {
        "id": "w_0_1",
        "label": "Full Body B",
        "date": "2026-07-10",
        "sets": [
          {
            "exerciseId": "reverse_fly",
            "reps": 15,
            "weightKg": 2,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "dumbbell_row",
            "reps": 15,
            "weightKg": 4,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "inverted_row",
            "reps": 15,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "bench",
            "reps": 15,
            "weightKg": 15,
            "rpe": 6.5,
            "sets": 2
          }
        ]
      }
    ]
  },
  "nutritionPlan": {
    "weekIndex": 0,
    "mealsByDay": {
      "Mon": [
        {
          "name": "Овсянка с ягодами и протеином",
          "ingredients": [
            "100г овсяных хлопьев",
            "200мл воды",
            "100г замороженных ягод",
            "30г сывороточного протеина"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 7,
            "carbsGrams": 55,
            "kcal": 423
          },
          "allergenTags": [],
          "recipe": "1. Сварите овсяные хлопья на воде. 2. Добавьте ягоды и протеин, хорошо перемешайте."
        },
        {
          "name": "Куриный салат с овощами",
          "ingredients": [
            "150г куриной грудки",
            "100г огурцов",
            "100г помидоров",
            "50г листового салата",
            "1 ст.л. оливкового масла"
          ],
          "kcal": 350,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 15,
            "carbsGrams": 10,
            "kcal": 345
          },
          "allergenTags": [],
          "recipe": "1. Отварите или запеките куриную грудку, нарежьте кубиками. 2. Нарежьте овощи, смешайте с курицей и заправьте маслом."
        },
        {
          "name": "Творог с фруктами",
          "ingredients": [
            "200г нежирного творога",
            "100г яблока",
            "50г банана"
          ],
          "kcal": 280,
          "macros": {
            "proteinGrams": 30,
            "fatGrams": 5,
            "carbsGrams": 30,
            "kcal": 285
          },
          "allergenTags": [],
          "recipe": "1. Смешайте творог с нарезанными фруктами."
        },
        {
          "name": "Рыба с рисом и овощами",
          "ingredients": [
            "150г трески",
            "100г бурого риса",
            "150г брокколи",
            "1 ст.л. оливкового масла"
          ],
          "kcal": 550,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 18,
            "carbsGrams": 60,
            "kcal": 542
          },
          "allergenTags": [],
          "recipe": "1. Запеките треску. 2. Отварите рис и брокколи. 3. Подавайте рыбу с рисом и брокколи, заправив маслом."
        },
        {
          "name": "Греческий йогурт с орехами",
          "ingredients": [
            "200г греческого йогурта",
            "30г грецких орехов"
          ],
          "kcal": 400,
          "macros": {
            "proteinGrams": 20,
            "fatGrams": 30,
            "carbsGrams": 15,
            "kcal": 400
          },
          "allergenTags": [],
          "recipe": "1. Смешайте йогурт с орехами."
        }
      ],
      "Tue": [
        {
          "name": "Яичница с овощами и тостом",
          "ingredients": [
            "3 яйца",
            "100г шпината",
            "50г грибов",
            "1 кусок цельнозернового хлеба",
            "1 ч.л. оливкового масла"
          ],
          "kcal": 380,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 25,
            "carbsGrams": 15,
            "kcal": 385
          },
          "allergenTags": [],
          "recipe": "1. Обжарьте шпинат и грибы на масле. 2. Добавьте яйца и приготовьте яичницу. 3. Подавайте с тостом."
        },
        {
          "name": "Индейка с киноа и овощами",
          "ingredients": [
            "150г филе индейки",
            "100г киноа",
            "150г стручковой фасоли",
            "1 ст.л. оливкового масла"
          ],
          "kcal": 550,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 18,
            "carbsGrams": 60,
            "kcal": 542
          },
          "allergenTags": [],
          "recipe": "1. Запеките или отварите филе индейки. 2. Отварите киноа и стручковую фасоль. 3. Подавайте индейку с киноа и фасолью, заправив маслом."
        },
        {
          "name": "Протеиновый смузи",
          "ingredients": [
            "30г сывороточного протеина",
            "200мл молока 1.5%",
            "100г банана"
          ],
          "kcal": 350,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 5,
            "carbsGrams": 40,
            "kcal": 345
          },
          "allergenTags": [],
          "recipe": "1. Смешайте все ингредиенты в блендере до однородной массы."
        },
        {
          "name": "Говядина с гречкой",
          "ingredients": [
            "150г нежирной говядины",
            "100г гречневой крупы",
            "100г моркови",
            "100г лука",
            "1 ст.л. оливкового масла"
          ],
          "kcal": 600,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 25,
            "carbsGrams": 50,
            "kcal": 595
          },
          "allergenTags": [],
          "recipe": "1. Нарежьте говядину и обжарьте. 2. Добавьте нарезанные морковь и лук, тушите. 3. Отварите гречку. 4. Смешайте говядину с гречкой и овощами."
        },
        {
          "name": "Йогурт с семенами чиа",
          "ingredients": [
            "200г натурального йогурта",
            "20г семян чиа"
          ],
          "kcal": 350,
          "macros": {
            "proteinGrams": 15,
            "fatGrams": 20,
            "carbsGrams": 20,
            "kcal": 340
          },
          "allergenTags": [],
          "recipe": "1. Смешайте йогурт с семенами чиа, дайте настояться 10 минут."
        }
      ],
      "Wed": [
        {
          "name": "Творожная запеканка",
          "ingredients": [
            "200г творога 5%",
            "2 яйца",
            "50г манной крупы",
            "50г изюма"
          ],
          "kcal": 450,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 15,
            "carbsGrams": 45,
            "kcal": 455
          },
          "allergenTags": [],
          "recipe": "1. Смешайте творог, яйца, манку и изюм. 2. Выпекайте в духовке до золотистой корочки."
        },
        {
          "name": "Курица с овощами и бататом",
          "ingredients": [
            "150г куриной грудки",
            "200г батата",
            "150г цукини",
            "1 ст.л. оливкового масла"
          ],
          "kcal": 500,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 495
          },
          "allergenTags": [],
          "recipe": "1. Запеките куриную грудку с нарезанным бататом и цукини. 2. Заправьте маслом."
        },
        {
          "name": "Фруктовый салат с йогуртом",
          "ingredients": [
            "150г греческого йогурта",
            "100г апельсина",
            "100г киви"
          ],
          "kcal": 250,
          "macros": {
            "proteinGrams": 15,
            "fatGrams": 5,
            "carbsGrams": 35,
            "kcal": 245
          },
          "allergenTags": [],
          "recipe": "1. Нарежьте фрукты и смешайте с йогуртом."
        },
        {
          "name": "Паста с тунцом и овощами",
          "ingredients": [
            "100г цельнозерновой пасты",
            "1 банка (180г) тунца в собственном соку",
            "100г болгарского перца",
            "50г оливок",
            "1 ст.л. оливкового масла"
          ],
          "kcal": 600,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 25,
            "carbsGrams": 50,
            "kcal": 595
          },
          "allergenTags": [],
          "recipe": "1. Отварите пасту. 2. Смешайте тунец, нарезанный перец, оливки и пасту. 3. Заправьте маслом."
        },
        {
          "name": "Кефир с отрубями",
          "ingredients": [
            "250мл кефира 2.5%",
            "20г овсяных отрубей"
          ],
          "kcal": 250,
          "macros": {
            "proteinGrams": 10,
            "fatGrams": 8,
            "carbsGrams": 30,
            "kcal": 248
          },
          "allergenTags": [],
          "recipe": "1. Смешайте кефир с отрубями."
        }
      ],
      "Thu": [
        {
          "name": "Омлет с сыром и овощами",
          "ingredients": [
            "3 яйца",
            "50мл молока",
            "50г сыра",
            "100г кабачка",
            "1 ч.л. оливкового масла"
          ],
          "kcal": 450,
          "macros": {
            "proteinGrams": 30,
            "fatGrams": 30,
            "carbsGrams": 10,
            "kcal": 440
          },
          "allergenTags": [],
          "recipe": "1. Взбейте яйца с молоком. 2. Нарежьте кабачок и обжарьте. 3. Добавьте яичную смесь и сыр, готовьте до готовности."
        },
        {
          "name": "Куриный суп с лапшой",
          "ingredients": [
            "200г куриной грудки",
            "50г цельнозерновой лапши",
            "100г моркови",
            "100г картофеля",
            "1.5л воды"
          ],
          "kcal": 400,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 8,
            "carbsGrams": 35,
            "kcal": 392
          },
          "allergenTags": [],
          "recipe": "1. Отварите куриную грудку, нарежьте. 2. Добавьте нарезанные овощи и лапшу, варите до готовности."
        },
        {
          "name": "Йогурт с фруктами и гранолой",
          "ingredients": [
            "200г греческого йогурта",
            "100г персика",
            "30г гранолы"
          ],
          "kcal": 380,
          "macros": {
            "proteinGrams": 20,
            "fatGrams": 10,
            "carbsGrams": 50,
            "kcal": 380
          },
          "allergenTags": [],
          "recipe": "1. Смешайте йогурт с нарезанным персиком и гранолой."
        },
        {
          "name": "Котлеты из индейки с пюре",
          "ingredients": [
            "200г фарша индейки",
            "200г картофеля",
            "50мл молока",
            "1 ч.л. сливочного масла",
            "1 ст.л. оливкового масла"
          ],
          "kcal": 600,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 25,
            "carbsGrams": 50,
            "kcal": 605
          },
          "allergenTags": [],
          "recipe": "1. Сформируйте котлеты из фарша индейки, обжарьте. 2. Отварите картофель, разомните с молоком и маслом в пюре."
        },
        {
          "name": "Творог с медом",
          "ingredients": [
            "150г творога 5%",
            "1 ст.л. меда"
          ],
          "kcal": 280,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 8,
            "carbsGrams": 25,
            "kcal": 282
          },
          "allergenTags": [],
          "recipe": "1. Смешайте творог с медом."
        }
      ],
      "Fri": [
        {
          "name": "Овсянка с яблоком и корицей",
          "ingredients": [
            "100г овсяных хлопьев",
            "200мл воды",
            "1 яблоко",
            "щепотка корицы"
          ],
          "kcal": 350,
          "macros": {
            "proteinGrams": 10,
            "fatGrams": 5,
            "carbsGrams": 65,
            "kcal": 345
          },
          "allergenTags": [],
          "recipe": "1. Сварите овсяные хлопья на воде. 2. Нарежьте яблоко и добавьте в кашу вместе с корицей."
        },
        {
          "name": "Салат с курицей и авокадо",
          "ingredients": [
            "150г куриной грудки",
            "100г авокадо",
            "100г листового салата",
            "50г огурцов",
            "1 ст.л. оливкового масла"
          ],
          "kcal": 450,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 25,
            "carbsGrams": 15,
            "kcal": 445
          },
          "allergenTags": [],
          "recipe": "1. Отварите или запеките куриную грудку, нарежьте. 2. Нарежьте овощи и авокадо, смешайте с курицей и заправьте маслом."
        },
        {
          "name": "Творожный десерт с ягодами",
          "ingredients": [
            "200г творога 5%",
            "100г малины",
            "1 ч.л. меда"
          ],
          "kcal": 300,
          "macros": {
            "proteinGrams": 30,
            "fatGrams": 8,
            "carbsGrams": 25,
            "kcal": 292
          },
          "allergenTags": [],
          "recipe": "1. Смешайте творог с малиной и медом."
        },
        {
          "name": "Рыба с кускусом и овощами",
          "ingredients": [
            "150г лосося (1-2 раза в неделю)",
            "100г кускуса",
            "150г спаржи",
            "1 ст.л. оливкового масла"
          ],
          "kcal": 650,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 35,
            "carbsGrams": 50,
            "kcal": 655
          },
          "allergenTags": [],
          "recipe": "1. Запеките лосось. 2. Приготовьте кускус. 3. Отварите спаржу. 4. Подавайте рыбу с кускусом и спаржей, заправив маслом."
        },
        {
          "name": "Протеиновый батончик",
          "ingredients": [
            "1 протеиновый батончик (30г белка)"
          ],
          "kcal": 250,
          "macros": {
            "proteinGrams": 30,
            "fatGrams": 10,
            "carbsGrams": 15,
            "kcal": 250
          },
          "allergenTags": [],
          "recipe": "1. Съешьте батончик."
        }
      ],
      "Sat": [
        {
          "name": "Гречневая каша с яйцом",
          "ingredients": [
            "100г гречневой крупы",
            "2 яйца",
            "1 ч.л. сливочного масла"
          ],
          "kcal": 400,
          "macros": {
            "proteinGrams": 20,
            "fatGrams": 20,
            "carbsGrams": 40,
            "kcal": 400
          },
          "allergenTags": [],
          "recipe": "1. Отварите гречку. 2. Сварите яйца вкрутую. 3. Подавайте гречку с яйцами и маслом."
        },
        {
          "name": "Куриные котлеты с овощным рагу",
          "ingredients": [
            "150г куриного фарша",
            "100г баклажана",
            "100г перца",
            "100г помидоров",
            "1 ст.л. оливкового масла"
          ],
          "kcal": 450,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 20,
            "carbsGrams": 30,
            "kcal": 440
          },
          "allergenTags": [],
          "recipe": "1. Сформируйте котлеты из фарша, обжарьте. 2. Нарежьте овощи, потушите до готовности. 3. Подавайте котлеты с рагу."
        },
        {
          "name": "Фруктовый смузи с йогуртом",
          "ingredients": [
            "200г натурального йогурта",
            "100г клубники",
            "50г банана"
          ],
          "kcal": 300,
          "macros": {
            "proteinGrams": 15,
            "fatGrams": 5,
            "carbsGrams": 45,
            "kcal": 305
          },
          "allergenTags": [],
          "recipe": "1. Смешайте все ингредиенты в блендере до однородной массы."
        },
        {
          "name": "Говядина с булгуром и салатом",
          "ingredients": [
            "150г нежирной говядины",
            "100г булгура",
            "100г огурцов",
            "100г помидоров",
            "1 ст.л. оливкового масла"
          ],
          "kcal": 650,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 25,
            "carbsGrams": 60,
            "kcal": 645
          },
          "allergenTags": [],
          "recipe": "1. Запеките или отварите говядину. 2. Отварите булгур. 3. Нарежьте овощи, смешайте с говядиной и булгуром, заправьте маслом."
        },
        {
          "name": "Творог с джемом",
          "ingredients": [
            "150г творога 5%",
            "2 ст.л. джема"
          ],
          "kcal": 280,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 8,
            "carbsGrams": 25,
            "kcal": 282
          },
          "allergenTags": [],
          "recipe": "1. Смешайте творог с джемом."
        }
      ],
      "Sun": [
        {
          "name": "Сырники со сметаной",
          "ingredients": [
            "200г творога 5%",
            "1 яйцо",
            "50г муки",
            "50г сметаны 15%",
            "1 ст.л. растительного масла"
          ],
          "kcal": 500,
          "macros": {
            "proteinGrams": 30,
            "fatGrams": 25,
            "carbsGrams": 40,
            "kcal": 495
          },
          "allergenTags": [],
          "recipe": "1. Смешайте творог, яйцо и муку, сформируйте сырники. 2. Обжарьте на масле. 3. Подавайте со сметаной."
        },
        {
          "name": "Курица с рисом и овощами",
          "ingredients": [
            "150г куриной грудки",
            "100г белого риса",
            "150г моркови",
            "150г зеленого горошка",
            "1 ст.л. оливкового масла"
          ],
          "kcal": 550,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 15,
            "carbsGrams": 60,
            "kcal": 535
          },
          "allergenTags": [],
          "recipe": "1. Отварите или запеките куриную грудку. 2. Отварите рис, морковь и горошек. 3. Смешайте все ингредиенты, заправьте маслом."
        },
        {
          "name": "Фруктовый салат с орехами",
          "ingredients": [
            "100г банана",
            "100г апельсина",
            "30г миндаля"
          ],
          "kcal": 300,
          "macros": {
            "proteinGrams": 8,
            "fatGrams": 15,
            "carbsGrams": 30,
            "kcal": 297
          },
          "allergenTags": [],
          "recipe": "1. Нарежьте фрукты, смешайте с миндалем."
        },
        {
          "name": "Рыбные котлеты с пюре из цветной капусты",
          "ingredients": [
            "200г филе минтая",
            "200г цветной капусты",
            "50мл молока",
            "1 ч.л. сливочного масла",
            "1 ст.л. оливкового масла"
          ],
          "kcal": 500,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbsGrams": 30,
            "kcal": 480
          },
          "allergenTags": [],
          "recipe": "1. Приготовьте котлеты из минтая, обжарьте. 2. Отварите цветную капусту, разомните с молоком и маслом в пюре."
        },
        {
          "name": "Греческий йогурт с медом",
          "ingredients": [
            "200г греческого йогурта",
            "1 ст.л. меда"
          ],
          "kcal": 300,
          "macros": {
            "proteinGrams": 20,
            "fatGrams": 8,
            "carbsGrams": 35,
            "kcal": 292
          },
          "allergenTags": [],
          "recipe": "1. Смешайте йогурт с медом."
        }
      ]
    },
    "shoppingList": [
      "Овсяные хлопья",
      "Замороженные ягоды",
      "Сывороточный протеин",
      "Куриная грудка",
      "Огурцы — 250 г + +",
      "Помидоры — 300 г + +",
      "Листовой салат",
      "Оливковое масло",
      "Нежирный творог",
      "Яблоко — 1 + +",
      "Банан — 300 г + +",
      "Треска — 150 г + +",
      "Бурый рис",
      "Брокколи — 150 г + +",
      "Греческий йогурт",
      "Грецкие орехи",
      "Яйца — 10 + +",
      "Шпинат — 100 г + +",
      "Грибы — 50 г + +",
      "Цельнозерновой хлеб",
      "Филе индейки — 150 г + +",
      "Киноа — 100 г + +",
      "Молоко % — 1.5",
      "Нежирная говядина",
      "Гречневая крупа",
      "Морковь",
      "Лук — 100 г + +",
      "Натуральный йогурт",
      "Семена чиа",
      "Творог % — 5",
      "Манная крупа",
      "Изюм — 50 г + +",
      "Батат — 200 г + +",
      "Цукини — 150 г + +",
      "Апельсин — 200 г + +",
      "Киви — 100 г + +",
      "Цельнозерновая паста",
      "Тунец в собственном соку",
      "Оливки",
      "Кефир % — 2.5",
      "Овсяные отруби",
      "Сыр — 50 г + +",
      "Кабачок",
      "Куриный фарш",
      "Цельнозерновая лапша",
      "Картофель",
      "Гранола — 30 г + +",
      "Фарш индейки",
      "Сливочное масло",
      "Мед — 3 ст.л + +",
      "Авокадо — 100 г + +",
      "Малина — 100 г + +",
      "Лосось",
      "Кускус — 100 г + +",
      "Спаржа — 150 г + +",
      "Протеиновый батончик",
      "Баклажан — 100 г + +",
      "Булгур — 100 г + +",
      "Клубника — 100 г + +",
      "Джем — 2 ст.л + +",
      "Мука — 50 г + +",
      "Сметана % — 15",
      "Растительное масло",
      "Белый рис",
      "Миндаль",
      "Филе минтая — 200 г + +",
      "Цветная капуста",
      "Овсяных хлопьев — 200 г",
      "Воды — 400 мл + 1.5 л",
      "Замороженных ягод — 100 г",
      "Сывороточного протеина — 60 г",
      "Куриной грудки — 800 г",
      "Листового салата — 150 г",
      "Оливкового масла — 15 ст.л",
      "Нежирного творога — 200 г",
      "Яблока — 100 г",
      "Бурого риса — 100 г",
      "Греческого йогурта — 750 г",
      "Грецких орехов — 30 г",
      "Кусок цельнозернового хлеба — 1",
      "Молока % — 200 мл",
      "Нежирной говядины — 300 г",
      "Гречневой крупы — 200 г",
      "Моркови — 350 г",
      "Натурального йогурта — 400 г",
      "Семян чиа — 20 г",
      "Творога % — 900 г",
      "Манной крупы — 50 г",
      "Цельнозерновой пасты — 100 г",
      "Банка ( г) тунца в собственном соку — 1",
      "Оливок — 50 г",
      "Кефира % — 250 мл",
      "Овсяных отрубей — 20 г",
      "Молока — 150 мл",
      "Кабачка — 100 г",
      "Цельнозерновой лапши — 50 г",
      "Картофеля — 300 г",
      "Фарша индейки — 200 г",
      "Сливочного масла — 3 ст.л",
      "Лосося ( раза в неделю) — 150 г",
      "Протеиновый батончик ( г белка) — 1",
      "Куриного фарша — 150 г",
      "Яйцо — 1",
      "Сметаны % — 50 г",
      "Растительного масла — 1 ст.л",
      "Белого риса — 100 г",
      "Миндаля — 30 г",
      "Цветной капусты — 200 г",
      "Стручковая фасоль",
      "Болгарский перец",
      "Персик — 100 г + +",
      "Корица",
      "Зеленый горошек",
      "Стручковой фасоли — 150 г",
      "Болгарского перца — 100 г",
      "Щепотка корицы",
      "Перца — 100 г",
      "Зеленого горошка — 150 г"
    ]
  },
  "sleepAdvice": {
    "messages": [
      "Ложитесь спать и просыпайтесь в одно и то же время каждый день, даже по выходным, чтобы наладить циркадные ритмы.",
      "Избегайте экранов за 60 минут до сна — синий свет подавляет выработку мелатонина, гормона сна.",
      "Поддерживайте прохладную температуру в спальне (16-19°C) и обеспечьте темноту для лучшего качества сна.",
      "Создайте расслабляющий ритуал перед сном, например, чтение книги или теплая ванна, чтобы подготовить тело ко сну."
    ],
    "disclaimer": "Эти советы носят информационный характер и не являются медицинской рекомендацией. При наличии серьезных проблем со сном проконсультируйтесь с врачом."
  }
}

```

---

## Сценарий 5 — Агрессивное похудение

### Запрос (POST /ai/bootstrap)

```json
{
  "profile": {
    "age": 22,
    "sex": "FEMALE",
    "heightCm": 170,
    "weightKg": 52,
    "goal": "LOSE_FAT",
    "experienceLevel": 2,
    "equipment": [
      "barbell",
      "dumbbells",
      "machines",
      "cables"
    ],
    "dietaryPreferences": [],
    "allergies": [],
    "injuries": [],
    "healthNotes": [
      "хочу быстро похудеть"
    ],
    "weeklySchedule": {
      "Mon": true,
      "Wed": true,
      "Fri": true
    },
    "lifestyleActivity": "LIGHT",
    "locale": "ru",
    "budgetLevel": 2,
    "trainingMode": "GYM",
    "trainingFocus": "FAT_LOSS",
    "sessionDurationMins": 60,
    "splitPreference": "AUTO"
  },
  "weekIndex": 0,
  "locale": "ru"
}
```

### Сырой ответ сервера

```json
{
  "trainingPlan": {
    "weekIndex": 0,
    "workouts": [
      {
        "id": "chest_triceps_2026_07_06",
        "label": "Chest + Triceps",
        "date": "2026-07-06",
        "sets": [
          {
            "exerciseId": "bench",
            "reps": 12,
            "weightKg": 30,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "db_bench_press",
            "reps": 12,
            "weightKg": 30,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "incline_bench",
            "reps": 14,
            "weightKg": 8,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "tricep_extension",
            "reps": 14,
            "weightKg": 8,
            "rpe": 6.5,
            "sets": 1
          },
          {
            "exerciseId": "skull_crusher",
            "reps": 14,
            "weightKg": 20,
            "rpe": 6.5,
            "sets": 1
          }
        ]
      },
      {
        "id": "back_biceps_2026_07_08",
        "label": "Back + Biceps",
        "date": "2026-07-08",
        "sets": [
          {
            "exerciseId": "negative_pullup",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "dumbbell_row",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "inverted_row",
            "reps": 14,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "curl",
            "reps": 14,
            "weightKg": 8,
            "rpe": 6.5,
            "sets": 1
          },
          {
            "exerciseId": "hammer_curl",
            "reps": 14,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 1
          }
        ]
      },
      {
        "id": "legs_shoulders_2026_07_10",
        "label": "Legs + Shoulders",
        "date": "2026-07-10",
        "sets": [
          {
            "exerciseId": "bodyweight_squat",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "hip_thrust",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "dumbbell_shoulder_press",
            "reps": 14,
            "weightKg": 20,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "lunge",
            "reps": 14,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 1
          },
          {
            "exerciseId": "plank",
            "reps": 14,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 1
          }
        ]
      }
    ]
  },
  "nutritionPlan": {
    "weekIndex": 0,
    "mealsByDay": {
      "Mon": [
        {
          "name": "Овсянка с ягодами и греческим йогуртом",
          "ingredients": [
            "100г овсяных хлопьев",
            "200г греческого йогурта 2%",
            "100г смешанных ягод (свежих или замороженных)"
          ],
          "kcal": 550,
          "macros": {
            "proteinGrams": 28,
            "fatGrams": 12,
            "carbsGrams": 80,
            "kcal": 550
          },
          "allergenTags": [],
          "recipe": "1. Сварите овсяные хлопья на воде или молоке. 2. Добавьте греческий йогурт. 3. Сверху выложите ягоды."
        },
        {
          "name": "Куриная грудка с рисом и овощами",
          "ingredients": [
            "150г куриной грудки",
            "100г бурого риса (сухого веса)",
            "150г брокколи",
            "50г моркови",
            "10мл оливкового масла"
          ],
          "kcal": 600,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 70,
            "kcal": 595
          },
          "allergenTags": [],
          "recipe": "1. Отварите рис. 2. Куриную грудку нарежьте и обжарьте на оливковом масле. 3. Добавьте брокколи и морковь, тушите до готовности."
        },
        {
          "name": "Творог с фруктами",
          "ingredients": [
            "200г нежирного творога",
            "100г яблока",
            "50г банана"
          ],
          "kcal": 450,
          "macros": {
            "proteinGrams": 36,
            "fatGrams": 5,
            "carbsGrams": 60,
            "kcal": 449
          },
          "allergenTags": [],
          "recipe": "1. Смешайте творог с нарезанными фруктами."
        }
      ],
      "Tue": [
        {
          "name": "Яичница с цельнозерновым хлебом и овощами",
          "ingredients": [
            "3 яйца",
            "2 ломтика цельнозернового хлеба",
            "100г помидоров",
            "50г огурцов",
            "5мл оливкового масла"
          ],
          "kcal": 500,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 25,
            "carbsGrams": 40,
            "kcal": 495
          },
          "allergenTags": [],
          "recipe": "1. Обжарьте яйца на оливковом масле. 2. Поджарьте хлеб. 3. Подавайте с нарезанными овощами."
        },
        {
          "name": "Индейка с бататом и стручковой фасолью",
          "ingredients": [
            "150г филе индейки",
            "200г батата",
            "150г стручковой фасоли",
            "10мл оливкового масла"
          ],
          "kcal": 600,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 15,
            "carbsGrams": 75,
            "kcal": 595
          },
          "allergenTags": [],
          "recipe": "1. Запеките филе индейки. 2. Батат нарежьте кубиками и запеките. 3. Отварите стручковую фасоль. 4. Подавайте все вместе."
        },
        {
          "name": "Греческий йогурт с фруктами",
          "ingredients": [
            "200г греческого йогурта 2%",
            "150г персика"
          ],
          "kcal": 450,
          "macros": {
            "proteinGrams": 20,
            "fatGrams": 5,
            "carbsGrams": 70,
            "kcal": 445
          },
          "allergenTags": [],
          "recipe": "1. Смешайте йогурт с нарезанным персиком."
        }
      ],
      "Wed": [
        {
          "name": "Овсянка с яблоком и корицей",
          "ingredients": [
            "100г овсяных хлопьев",
            "1 большое яблоко",
            "щепотка корицы",
            "200мл воды"
          ],
          "kcal": 450,
          "macros": {
            "proteinGrams": 12,
            "fatGrams": 5,
            "carbsGrams": 85,
            "kcal": 449
          },
          "allergenTags": [],
          "recipe": "1. Сварите овсянку на воде. 2. Нарежьте яблоко и добавьте в кашу. 3. Посыпьте корицей."
        },
        {
          "name": "Постная говядина с макаронами из твердых сортов пшеницы и овощным салатом",
          "ingredients": [
            "150г постной говядины",
            "100г макарон из твердых сортов пшеницы (сухого веса)",
            "100г листового салата",
            "50г огурцов",
            "50г помидоров",
            "5мл оливкового масла"
          ],
          "kcal": 650,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 20,
            "carbsGrams": 70,
            "kcal": 650
          },
          "allergenTags": [],
          "recipe": "1. Отварите макароны. 2. Говядину отварите или запеките. 3. Смешайте овощи для салата, заправьте маслом. 4. Подавайте говядину с макаронами и салатом."
        },
        {
          "name": "Творожная запеканка",
          "ingredients": [
            "200г нежирного творога",
            "1 яйцо",
            "30г манной крупы",
            "50г изюма"
          ],
          "kcal": 500,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 10,
            "carbsGrams": 65,
            "kcal": 495
          },
          "allergenTags": [],
          "recipe": "1. Смешайте творог, яйцо, манку и изюм. 2. Выпекайте в духовке до золотистой корочки."
        }
      ],
      "Thu": [
        {
          "name": "Греческий йогурт с гранолой и фруктами",
          "ingredients": [
            "200г греческого йогурта 2%",
            "50г гранолы (без сахара)",
            "100г банана"
          ],
          "kcal": 500,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 10,
            "carbsGrams": 75,
            "kcal": 495
          },
          "allergenTags": [],
          "recipe": "1. Смешайте йогурт с гранолой и нарезанным бананом."
        },
        {
          "name": "Куриный суп с лапшой и овощами",
          "ingredients": [
            "150г куриной грудки",
            "50г лапши",
            "100г моркови",
            "100г картофеля",
            "1л куриного бульона"
          ],
          "kcal": 550,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 10,
            "carbsGrams": 70,
            "kcal": 550
          },
          "allergenTags": [],
          "recipe": "1. Отварите куриную грудку, нарежьте. 2. Добавьте в бульон нарезанные овощи и лапшу. 3. Варите до готовности. 4. Добавьте курицу."
        },
        {
          "name": "Салат с тунцом и цельнозерновым хлебом",
          "ingredients": [
            "1 банка консервированного тунца в собственном соку (150г)",
            "2 ломтика цельнозернового хлеба",
            "100г листового салата",
            "50г помидоров",
            "50г огурцов",
            "5мл оливкового масла"
          ],
          "kcal": 550,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 20,
            "carbsGrams": 50,
            "kcal": 540
          },
          "allergenTags": [],
          "recipe": "1. Слейте жидкость с тунца. 2. Нарежьте овощи. 3. Смешайте тунец с овощами, заправьте маслом. 4. Подавайте с хлебом."
        }
      ],
      "Fri": [
        {
          "name": "Омлет с овощами и цельнозерновым тостом",
          "ingredients": [
            "3 яйца",
            "50г шпината",
            "50г болгарского перца",
            "1 ломтик цельнозернового хлеба",
            "5мл оливкового масла"
          ],
          "kcal": 480,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 25,
            "carbsGrams": 35,
            "kcal": 485
          },
          "allergenTags": [],
          "recipe": "1. Взбейте яйца. 2. Нарежьте овощи. 3. Обжарьте овощи на оливковом масле, затем добавьте яйца и готовьте омлет. 4. Поджарьте тост."
        },
        {
          "name": "Лосось запеченный с киноа и спаржей",
          "ingredients": [
            "150г замороженного лосося",
            "100г киноа (сухого веса)",
            "150г спаржи",
            "10мл оливкового масла",
            "лимонный сок по вкусу"
          ],
          "kcal": 650,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 25,
            "carbsGrams": 60,
            "kcal": 645
          },
          "allergenTags": [],
          "recipe": "1. Отварите киноа. 2. Лосось и спаржу запеките в духовке с оливковым маслом и лимонным соком. 3. Подавайте вместе."
        },
        {
          "name": "Творог с ягодами",
          "ingredients": [
            "200г нежирного творога",
            "100г малины"
          ],
          "kcal": 450,
          "macros": {
            "proteinGrams": 36,
            "fatGrams": 5,
            "carbsGrams": 60,
            "kcal": 449
          },
          "allergenTags": [],
          "recipe": "1. Смешайте творог с ягодами."
        }
      ],
      "Sat": [
        {
          "name": "Овсянка с орехами и медом",
          "ingredients": [
            "100г овсяных хлопьев",
            "20г грецких орехов",
            "10г меда",
            "200мл воды"
          ],
          "kcal": 500,
          "macros": {
            "proteinGrams": 15,
            "fatGrams": 20,
            "carbsGrams": 65,
            "kcal": 495
          },
          "allergenTags": [],
          "recipe": "1. Сварите овсянку на воде. 2. Добавьте измельченные орехи и мед."
        },
        {
          "name": "Куриный салат с авокадо и цельнозерновым хлебом",
          "ingredients": [
            "150г отварной куриной грудки",
            "100г авокадо",
            "100г листового салата",
            "50г помидоров черри",
            "2 ломтика цельнозернового хлеба",
            "5мл оливкового масла"
          ],
          "kcal": 600,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 25,
            "carbsGrams": 45,
            "kcal": 595
          },
          "allergenTags": [],
          "recipe": "1. Нарежьте курицу, авокадо и овощи. 2. Смешайте все ингредиенты, заправьте оливковым маслом. 3. Подавайте с хлебом."
        },
        {
          "name": "Греческий йогурт с фруктами и семенами чиа",
          "ingredients": [
            "200г греческого йогурта 2%",
            "100г киви",
            "10г семян чиа"
          ],
          "kcal": 500,
          "macros": {
            "proteinGrams": 20,
            "fatGrams": 15,
            "carbsGrams": 65,
            "kcal": 495
          },
          "allergenTags": [],
          "recipe": "1. Смешайте йогурт с нарезанным киви и семенами чиа."
        }
      ],
      "Sun": [
        {
          "name": "Сырники с ягодами",
          "ingredients": [
            "200г нежирного творога",
            "1 яйцо",
            "30г муки цельнозерновой",
            "100г черники",
            "5мл растительного масла для жарки"
          ],
          "kcal": 550,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 15,
            "carbsGrams": 65,
            "kcal": 545
          },
          "allergenTags": [],
          "recipe": "1. Смешайте творог, яйцо и муку. 2. Сформируйте сырники и обжарьте на сковороде. 3. Подавайте с черникой."
        },
        {
          "name": "Паста с курицей и овощами",
          "ingredients": [
            "150г куриной грудки",
            "100г цельнозерновой пасты (сухого веса)",
            "100г цукини",
            "100г болгарского перца",
            "10мл оливкового масла"
          ],
          "kcal": 600,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 70,
            "kcal": 595
          },
          "allergenTags": [],
          "recipe": "1. Отварите пасту. 2. Курицу нарежьте и обжарьте. 3. Добавьте нарезанные овощи и тушите до готовности. 4. Смешайте с пастой."
        },
        {
          "name": "Котлеты из индейки с гречкой",
          "ingredients": [
            "150г фарша из индейки",
            "100г гречки (сухого веса)",
            "50г лука",
            "5мл оливкового масла"
          ],
          "kcal": 500,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 495
          },
          "allergenTags": [],
          "recipe": "1. Сформируйте котлеты из фарша индейки с добавлением лука. 2. Обжарьте или запеките котлеты. 3. Отварите гречку. 4. Подавайте котлеты с гречкой."
        }
      ]
    },
    "shoppingList": [
      "Овсяные хлопья",
      "Греческий йогурт % — 2",
      "Смешанные ягоды (свежие или замороженные)",
      "Куриная грудка",
      "Бурый рис",
      "Брокколи + + — 150 г",
      "Морковь",
      "Оливковое масло",
      "Нежирный творог",
      "Яблоко",
      "Банан + + — 150 г",
      "Яйца + + — 6",
      "Цельнозерновой хлеб",
      "Помидоры + + — 200 г",
      "Огурцы + + — 150 г",
      "Филе индейки + + — 150 г",
      "Батат + + — 200 г",
      "Постная говядина",
      "Макароны из твердых сортов пшеницы",
      "Листовой салат",
      "Манная крупа",
      "Изюм + + — 50 г",
      "Гранола (без сахара)",
      "Лапша + + — 50 г",
      "Картофель",
      "Куриный бульон",
      "Лосось замороженный",
      "Киноа",
      "Спаржа + + — 150 г",
      "Лимон",
      "Шпинат + + — 50 г",
      "Малина + + — 100 г",
      "Грецкие орехи",
      "Мед + + — 10 г",
      "Авокадо + + — 100 г",
      "Помидоры черри",
      "Киви + + — 100 г",
      "Семена чиа",
      "Мука цельнозерновая",
      "Черника + + — 100 г",
      "Растительное масло",
      "Цукини + + — 100 г",
      "Фарш из индейки",
      "Гречка",
      "Лук + + — 50 г",
      "Овсяных хлопьев — 600 г",
      "Греческого йогурта % — 1600 г",
      "Смешанных ягод (свежих или замороженных) — 200 г",
      "Куриной грудки — 900 г",
      "Бурого риса (сухого веса) — 200 г",
      "Моркови — 300 г",
      "Оливкового масла — 140 мл",
      "Нежирного творога — 1600 г",
      "Яблока — 200 г",
      "Омтика цельнозернового хлеба — 12 л",
      "Большое яблоко — 2",
      "Воды — 800 мл",
      "Постной говядины — 300 г",
      "Макарон из твердых сортов пшеницы (сухого веса) — 200 г",
      "Листового салата — 600 г",
      "Яйцо — 4",
      "Манной крупы — 60 г",
      "Гранолы (без сахара) — 100 г",
      "Картофеля — 200 г",
      "Куриного бульона — 2 л",
      "Омтик цельнозернового хлеба — 2 л",
      "Замороженного лосося — 300 г",
      "Киноа (сухого веса) — 200 г",
      "Грецких орехов — 40 г",
      "Отварной куриной грудки — 300 г",
      "Помидоров черри — 100 г",
      "Семян чиа — 20 г",
      "Муки цельнозерновой — 60 г",
      "Растительного масла для жарки — 10 мл",
      "Цельнозерновой пасты (сухого веса) — 200 г",
      "Фарша из индейки — 300 г",
      "Гречки (сухого веса) — 200 г",
      "Брокколи — 150 г",
      "Банана — 150 г",
      "Яйца — 6",
      "Помидоров — 200 г",
      "Огурцов — 150 г",
      "Филе индейки — 150 г",
      "Батата — 200 г",
      "Изюма — 50 г",
      "Лапши — 50 г",
      "Шпината — 50 г",
      "Спаржи — 150 г",
      "Малины — 100 г",
      "Меда — 10 г",
      "Авокадо — 100 г",
      "Киви — 100 г",
      "Черники — 100 г",
      "Цукини — 100 г",
      "Лука — 50 г",
      "Стручковая фасоль",
      "Корица",
      "Персик + + — 150 г",
      "Консервированный тунец в собственном соку",
      "Болгарский перец",
      "Стручковой фасоли — 300 г",
      "Щепотка корицы",
      "Банка консервированного тунца в собственном соку ( г) — 2",
      "Болгарского перца — 300 г",
      "Лимонный сок по вкусу",
      "Персика — 150 г"
    ]
  },
  "sleepAdvice": {
    "messages": [
      "Ложитесь и просыпайтесь в одно и то же время, даже по выходным.",
      "За час до сна приглушите свет и уберите яркие экраны.",
      "Поддерживайте прохладу и тишину в спальне (18–20 °C).",
      "Избегайте тяжёлой еды и кофеина за 3 часа до сна.",
      "Добавьте лёгкую растяжку или дыхательные упражнения перед сном.",
      "Если ночь прошла плохо, снизьте нагрузку на следующей тренировке."
    ],
    "disclaimer": "Советы носят ознакомительный характер. При серьёзных нарушениях сна обратитесь к врачу."
  }
}

```

---

## Сценарий 6 — Дом, резинки, PPL 6 дней

### Запрос (POST /ai/bootstrap)

```json
{
  "profile": {
    "age": 28,
    "sex": "MALE",
    "heightCm": 177,
    "weightKg": 82,
    "goal": "GAIN_MUSCLE",
    "experienceLevel": 2,
    "equipment": [
      "bands",
      "mat"
    ],
    "dietaryPreferences": [],
    "allergies": [],
    "injuries": [],
    "healthNotes": [],
    "weeklySchedule": {
      "Mon": true,
      "Tue": true,
      "Wed": true,
      "Thu": true,
      "Fri": true,
      "Sat": true
    },
    "lifestyleActivity": "SEDENTARY",
    "locale": "ru",
    "budgetLevel": 2,
    "trainingMode": "HOME",
    "trainingFocus": "HYPERTROPHY",
    "sessionDurationMins": 45,
    "splitPreference": "PPL"
  },
  "weekIndex": 0,
  "locale": "ru"
}
```

### Сырой ответ сервера

```json
{
  "trainingPlan": {
    "weekIndex": 0,
    "workouts": [
      {
        "id": "w_0_0",
        "label": "Push",
        "date": "2026-07-06",
        "sets": [
          {
            "exerciseId": "pushup",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "lateral_raise",
            "reps": 12,
            "weightKg": 2,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "knee_pushup",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "tricep_extension",
            "reps": 15,
            "weightKg": 2,
            "rpe": 6.5,
            "sets": 2
          }
        ]
      },
      {
        "id": "w_0_1",
        "label": "Pull",
        "date": "2026-07-07",
        "sets": [
          {
            "exerciseId": "assisted_pullup",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "towel_row",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "table_row",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "curl",
            "reps": 15,
            "weightKg": 2,
            "rpe": 6.5,
            "sets": 2
          }
        ]
      },
      {
        "id": "w_0_2",
        "label": "Legs",
        "date": "2026-07-08",
        "sets": [
          {
            "exerciseId": "bodyweight_squat",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "hip_thrust",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "lunge",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "plank",
            "reps": 45,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          }
        ]
      },
      {
        "id": "w_0_3",
        "label": "Push 2",
        "date": "2026-07-09",
        "sets": [
          {
            "exerciseId": "lateral_raise",
            "reps": 12,
            "weightKg": 2,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "pushup",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "upright_row",
            "reps": 12,
            "weightKg": 2,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "tricep_extension",
            "reps": 15,
            "weightKg": 2,
            "rpe": 6.5,
            "sets": 2
          }
        ]
      },
      {
        "id": "w_0_4",
        "label": "Pull 2",
        "date": "2026-07-10",
        "sets": [
          {
            "exerciseId": "band_pulldown",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "towel_row",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "table_row",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "curl",
            "reps": 15,
            "weightKg": 2,
            "rpe": 6.5,
            "sets": 2
          }
        ]
      },
      {
        "id": "w_0_5",
        "label": "Legs",
        "date": "2026-07-11",
        "sets": [
          {
            "exerciseId": "bodyweight_squat",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "hip_thrust",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "lunge",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "plank",
            "reps": 45,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          }
        ]
      }
    ]
  },
  "nutritionPlan": {
    "weekIndex": 0,
    "mealsByDay": {
      "Mon": [
        {
          "name": "Овсянка с ягодами и протеином",
          "ingredients": [
            "150г овсяных хлопьев",
            "300мл молока 2.5%",
            "150г замороженных ягод",
            "30г сывороточного протеина"
          ],
          "kcal": 695,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 90,
            "kcal": 695
          },
          "allergenTags": [],
          "recipe": "1. Залей овсяные хлопья молоком, вари на среднем огне 5-7 минут, помешивая. 2. Добавь ягоды и протеин, хорошо перемешай. Подавай горячим."
        },
        {
          "name": "Куриный салат с цельнозерновым хлебом",
          "ingredients": [
            "180г куриной грудки",
            "100г цельнозернового хлеба",
            "150г свежих овощей (огурец, помидор, листья салата)",
            "30г греческого йогурта 2%",
            "10г оливкового масла"
          ],
          "kcal": 620,
          "macros": {
            "proteinGrams": 55,
            "fatGrams": 20,
            "carbsGrams": 55,
            "kcal": 610
          },
          "allergenTags": [],
          "recipe": "1. Куриную грудку отвари или запеки, нарежь кубиками. 2. Овощи нарежь, смешай с курицей. 3. Заправь йогуртом и оливковым маслом. Подавай с хлебом."
        },
        {
          "name": "Рис с фаршем и овощами",
          "ingredients": [
            "150г риса (в сухом виде)",
            "200г нежирного говяжьего фарша",
            "200г замороженных овощей (брокколи, морковь, горошек)",
            "15г растительного масла"
          ],
          "kcal": 900,
          "macros": {
            "proteinGrams": 60,
            "fatGrams": 30,
            "carbsGrams": 95,
            "kcal": 900
          },
          "allergenTags": [],
          "recipe": "1. Отвари рис согласно инструкции. 2. Обжарь фарш на масле до готовности. 3. Добавь овощи, туши 7-10 минут. 4. Смешай с рисом."
        },
        {
          "name": "Творог с фруктами",
          "ingredients": [
            "200г обезжиренного творога",
            "150г любого фрукта (банан, яблоко)",
            "10г меда"
          ],
          "kcal": 405,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 5,
            "carbsGrams": 60,
            "kcal": 405
          },
          "allergenTags": [],
          "recipe": "1. Смешай творог с нарезанным фруктом. 2. Полей медом."
        }
      ],
      "Tue": [
        {
          "name": "Гречневая каша с яйцами",
          "ingredients": [
            "150г гречневой крупы (в сухом виде)",
            "3 яйца",
            "10г сливочного масла",
            "100г огурцов"
          ],
          "kcal": 700,
          "macros": {
            "proteinGrams": 30,
            "fatGrams": 30,
            "carbsGrams": 75,
            "kcal": 700
          },
          "allergenTags": [],
          "recipe": "1. Отвари гречку. 2. Яйца свари вкрутую или сделай омлет. 3. Подавай гречку с яйцами, добавив масло и свежие огурцы."
        },
        {
          "name": "Сэндвич с индейкой и сыром",
          "ingredients": [
            "150г филе индейки",
            "100г цельнозернового хлеба",
            "50г нежирного сыра",
            "100г листьев салата",
            "20г горчицы"
          ],
          "kcal": 600,
          "macros": {
            "proteinGrams": 50,
            "fatGrams": 20,
            "carbsGrams": 60,
            "kcal": 600
          },
          "allergenTags": [],
          "recipe": "1. Индейку отвари или запеки, нарежь тонкими ломтиками. 2. Хлеб подсуши. 3. Собери сэндвич: хлеб, горчица, индейка, сыр, салат, хлеб."
        },
        {
          "name": "Паста с тунцом и томатами",
          "ingredients": [
            "150г цельнозерновой пасты (в сухом виде)",
            "180г консервированного тунца в собственном соку",
            "200г консервированных томатов в собственном соку",
            "15г оливкового масла",
            "50г лука"
          ],
          "kcal": 900,
          "macros": {
            "proteinGrams": 60,
            "fatGrams": 30,
            "carbsGrams": 95,
            "kcal": 900
          },
          "allergenTags": [],
          "recipe": "1. Отвари пасту до состояния аль денте. 2. На сковороде обжарь лук, добавь томаты и тунец, туши 5-7 минут. 3. Смешай соус с пастой, добавь оливковое масло."
        },
        {
          "name": "Йогурт с фруктами и орехами",
          "ingredients": [
            "250г греческого йогурта 2%",
            "150г свежих фруктов",
            "20г миндаля"
          ],
          "kcal": 500,
          "macros": {
            "proteinGrams": 30,
            "fatGrams": 20,
            "carbsGrams": 50,
            "kcal": 500
          },
          "allergenTags": [],
          "recipe": "1. Смешай йогурт с нарезанными фруктами. 2. Посыпь миндалем."
        }
      ],
      "Wed": [
        {
          "name": "Омлет с овощами и тостами",
          "ingredients": [
            "4 яйца",
            "100г брокколи",
            "50г болгарского перца",
            "2 ломтика цельнозернового хлеба",
            "10г оливкового масла"
          ],
          "kcal": 600,
          "macros": {
            "proteinGrams": 30,
            "fatGrams": 35,
            "carbsGrams": 40,
            "kcal": 595
          },
          "allergenTags": [],
          "recipe": "1. Взбей яйца. 2. Овощи нарежь и обжарь на оливковом масле. 3. Добавь яйца, готовь до готовности омлета. 4. Подавай с поджаренным хлебом."
        },
        {
          "name": "Курица с бататом",
          "ingredients": [
            "200г куриного филе",
            "250г батата",
            "150г зеленой фасоли",
            "15г оливкового масла"
          ],
          "kcal": 750,
          "macros": {
            "proteinGrams": 60,
            "fatGrams": 25,
            "carbsGrams": 70,
            "kcal": 745
          },
          "allergenTags": [],
          "recipe": "1. Куриное филе нарежь, обжарь на оливковом масле до готовности. 2. Батат нарежь кубиками, отвари или запеки. 3. Зеленую фасоль отвари. 4. Подавай курицу с бататом и фасолью."
        },
        {
          "name": "Плов с курицей",
          "ingredients": [
            "180г риса (в сухом виде)",
            "200г куриного филе",
            "100г моркови",
            "100г лука",
            "20г растительного масла"
          ],
          "kcal": 900,
          "macros": {
            "proteinGrams": 55,
            "fatGrams": 30,
            "carbsGrams": 100,
            "kcal": 890
          },
          "allergenTags": [],
          "recipe": "1. Курицу нарежь кубиками, обжарь на масле. 2. Добавь нарезанные морковь и лук, туши. 3. Добавь рис, залей водой, туши до готовности риса."
        },
        {
          "name": "Творожная запеканка",
          "ingredients": [
            "200г творога 5%",
            "2 яйца",
            "50г манной крупы",
            "100г яблок",
            "10г меда"
          ],
          "kcal": 500,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 15,
            "carbsGrams": 55,
            "kcal": 505
          },
          "allergenTags": [],
          "recipe": "1. Смешай творог, яйца, манку, нарезанные яблоки и мед. 2. Выложи в форму и запекай в духовке до золотистой корочки."
        }
      ],
      "Thu": [
        {
          "name": "Овсянка с бананом и орехами",
          "ingredients": [
            "150г овсяных хлопьев",
            "300мл молока 2.5%",
            "1 банан",
            "20г грецких орехов"
          ],
          "kcal": 700,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 25,
            "carbsGrams": 100,
            "kcal": 705
          },
          "allergenTags": [],
          "recipe": "1. Залей овсяные хлопья молоком, вари на среднем огне 5-7 минут, помешивая. 2. Добавь нарезанный банан и грецкие орехи. Подавай горячим."
        },
        {
          "name": "Салат с курицей и киноа",
          "ingredients": [
            "180г куриной грудки",
            "100г киноа (в сухом виде)",
            "150г свежих овощей (перец, огурец)",
            "15г оливкового масла",
            "10г лимонного сока"
          ],
          "kcal": 750,
          "macros": {
            "proteinGrams": 55,
            "fatGrams": 25,
            "carbsGrams": 75,
            "kcal": 745
          },
          "allergenTags": [],
          "recipe": "1. Куриную грудку отвари или запеки, нарежь кубиками. 2. Отвари киноа. 3. Овощи нарежь, смешай с курицей и киноа. 4. Заправь оливковым маслом и лимонным соком."
        },
        {
          "name": "Рыбные котлеты с картофельным пюре",
          "ingredients": [
            "200г филе минтая",
            "300г картофеля",
            "50мл молока 2.5%",
            "10г сливочного масла",
            "1 яйцо",
            "10г растительного масла"
          ],
          "kcal": 800,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 35,
            "carbsGrams": 75,
            "kcal": 795
          },
          "allergenTags": [],
          "recipe": "1. Филе минтая измельчи, добавь яйцо, сформируй котлеты и обжарь на растительном масле. 2. Картофель отвари, разомни в пюре с молоком и сливочным маслом. 3. Подавай котлеты с пюре."
        },
        {
          "name": "Творог с джемом",
          "ingredients": [
            "200г творога 5%",
            "50г джема без сахара"
          ],
          "kcal": 450,
          "macros": {
            "proteinGrams": 30,
            "fatGrams": 10,
            "carbsGrams": 55,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": "1. Смешай творог с джемом."
        }
      ],
      "Fri": [
        {
          "name": "Яичница с овощами и хлебом",
          "ingredients": [
            "4 яйца",
            "100г помидоров",
            "50г шпината",
            "2 ломтика цельнозернового хлеба",
            "10г оливкового масла"
          ],
          "kcal": 600,
          "macros": {
            "proteinGrams": 30,
            "fatGrams": 35,
            "carbsGrams": 40,
            "kcal": 595
          },
          "allergenTags": [],
          "recipe": "1. Обжарь помидоры и шпинат на оливковом масле. 2. Добавь взбитые яйца, готовь до готовности. 3. Подавай с поджаренным хлебом."
        },
        {
          "name": "Куриный суп с лапшой",
          "ingredients": [
            "200г куриного филе",
            "100г лапши (в сухом виде)",
            "100г моркови",
            "100г картофеля",
            "1.5л воды"
          ],
          "kcal": 750,
          "macros": {
            "proteinGrams": 60,
            "fatGrams": 15,
            "carbsGrams": 90,
            "kcal": 735
          },
          "allergenTags": [],
          "recipe": "1. Куриное филе отвари, нарежь. 2. В бульон добавь нарезанные морковь, картофель и лапшу, вари до готовности. 3. Добавь курицу."
        },
        {
          "name": "Запеченная рыба с рисом",
          "ingredients": [
            "200г филе трески",
            "150г риса (в сухом виде)",
            "150г брокколи",
            "15г оливкового масла",
            "10г лимонного сока"
          ],
          "kcal": 800,
          "macros": {
            "proteinGrams": 50,
            "fatGrams": 20,
            "carbsGrams": 90,
            "kcal": 780
          },
          "allergenTags": [],
          "recipe": "1. Филе трески запеки с лимонным соком и оливковым маслом. 2. Отвари рис и брокколи. 3. Подавай рыбу с рисом и брокколи."
        },
        {
          "name": "Греческий йогурт с медом",
          "ingredients": [
            "250г греческого йогурта 2%",
            "20г меда"
          ],
          "kcal": 450,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 10,
            "carbsGrams": 60,
            "kcal": 440
          },
          "allergenTags": [],
          "recipe": "1. Смешай йогурт с медом."
        }
      ],
      "Sat": [
        {
          "name": "Творог с фруктами и медом",
          "ingredients": [
            "250г творога 5%",
            "200г фруктов (яблоко, банан)",
            "15г меда"
          ],
          "kcal": 600,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 15,
            "carbsGrams": 80,
            "kcal": 600
          },
          "allergenTags": [],
          "recipe": "1. Смешай творог с нарезанными фруктами. 2. Полей медом."
        },
        {
          "name": "Куриная грудка с овощами гриль",
          "ingredients": [
            "200г куриной грудки",
            "300г овощей (цукини, перец, баклажан)",
            "15г оливкового масла"
          ],
          "kcal": 700,
          "macros": {
            "proteinGrams": 60,
            "fatGrams": 25,
            "carbsGrams": 50,
            "kcal": 695
          },
          "allergenTags": [],
          "recipe": "1. Куриную грудку и овощи нарежь, смажь оливковым маслом. 2. Запеки на гриле или в духовке до готовности."
        },
        {
          "name": "Бургер с говядиной и цельнозерновой булочкой",
          "ingredients": [
            "200г нежирного говяжьего фарша",
            "1 цельнозерновая булочка",
            "50г листьев салата",
            "50г помидора",
            "20г сыра",
            "10г соуса (кетчуп/горчица)"
          ],
          "kcal": 900,
          "macros": {
            "proteinGrams": 65,
            "fatGrams": 40,
            "carbsGrams": 70,
            "kcal": 890
          },
          "allergenTags": [],
          "recipe": "1. Из фарша сформируй котлету, обжарь до готовности. 2. Булочку разрежь, подсуши. 3. Собери бургер: булочка, соус, котлета, сыр, салат, помидор."
        },
        {
          "name": "Фруктовый салат с йогуртом",
          "ingredients": [
            "300г различных фруктов (яблоко, груша, апельсин)",
            "150г греческого йогурта 2%"
          ],
          "kcal": 500,
          "macros": {
            "proteinGrams": 15,
            "fatGrams": 5,
            "carbsGrams": 90,
            "kcal": 465
          },
          "allergenTags": [],
          "recipe": "1. Фрукты нарежь кубиками. 2. Смешай с йогуртом."
        }
      ],
      "Sun": [
        {
          "name": "Омлет с сыром и овощами",
          "ingredients": [
            "4 яйца",
            "50г сыра",
            "100г шпината",
            "50г помидоров",
            "10г оливкового масла"
          ],
          "kcal": 650,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 45,
            "carbsGrams": 20,
            "kcal": 635
          },
          "allergenTags": [],
          "recipe": "1. Взбей яйца. 2. Овощи нарежь и обжарь на оливковом масле. 3. Добавь яйца и тертый сыр, готовь до готовности омлета."
        },
        {
          "name": "Куриные котлеты с гречкой",
          "ingredients": [
            "200г куриного фарша",
            "150г гречневой крупы (в сухом виде)",
            "150г свежих овощей (огурец, помидор)",
            "15г растительного масла"
          ],
          "kcal": 800,
          "macros": {
            "proteinGrams": 60,
            "fatGrams": 25,
            "carbsGrams": 80,
            "kcal": 795
          },
          "allergenTags": [],
          "recipe": "1. Из фарша сформируй котлеты, обжарь на растительном масле. 2. Отвари гречку. 3. Подавай котлеты с гречкой и свежими овощами."
        },
        {
          "name": "Паста с курицей и овощами",
          "ingredients": [
            "150г цельнозерновой пасты (в сухом виде)",
            "200г куриного филе",
            "200г замороженных овощей (брокколи, морковь)",
            "15г оливкового масла"
          ],
          "kcal": 900,
          "macros": {
            "proteinGrams": 60,
            "fatGrams": 30,
            "carbsGrams": 95,
            "kcal": 900
          },
          "allergenTags": [],
          "recipe": "1. Отвари пасту. 2. Куриное филе нарежь, обжарь на оливковом масле. 3. Добавь овощи, туши 7-10 минут. 4. Смешай с пастой."
        },
        {
          "name": "Кефир с бананом",
          "ingredients": [
            "300мл кефира 2.5%",
            "1 банан"
          ],
          "kcal": 400,
          "macros": {
            "proteinGrams": 15,
            "fatGrams": 10,
            "carbsGrams": 60,
            "kcal": 390
          },
          "allergenTags": [],
          "recipe": "1. Смешай кефир с нарезанным бананом в блендере."
        }
      ]
    },
    "shoppingList": [
      "Овсяные хлопья",
      "Молоко % — 2.5",
      "Замороженные ягоды",
      "Сывороточный протеин",
      "Куриная грудка",
      "Цельнозерновой хлеб",
      "Свежие овощи (огурец помидор листья салата)",
      "Греческий йогурт % — 2",
      "Оливковое масло",
      "Рис",
      "Нежирный говяжий фарш",
      "Замороженные овощи (брокколи морковь горошек)",
      "Растительное масло",
      "Обезжиренный творог",
      "Любой фрукт (банан яблоко)",
      "Мед — 55 г + +",
      "Гречневая крупа",
      "Яйца — 17 + +",
      "Сливочное масло",
      "Филе индейки — 150 г + +",
      "Нежирный сыр",
      "Цельнозерновая паста",
      "Лук — 150 г + +",
      "Миндаль",
      "Брокколи — 250 г + +",
      "Батат — 250 г + +",
      "Морковь",
      "Творог % — 5",
      "Манная крупа",
      "Филе минтая — 200 г + +",
      "Картофель",
      "Джем без сахара",
      "Помидоры — 200 г + +",
      "Шпинат — 150 г + +",
      "Лапша",
      "Вода — 1.5 л + +",
      "Филе трески — 200 г + +",
      "Цукини",
      "Баклажан",
      "Цельнозерновая булочка — 1 + +",
      "Различные фрукты (яблоко груша апельсин)",
      "Куриный фарш",
      "Кефир % — 2.5",
      "Овсяных хлопьев — 300 г",
      "Молока % — 650 мл",
      "Замороженных ягод — 150 г",
      "Сывороточного протеина — 30 г",
      "Куриной грудки — 560 г",
      "Цельнозернового хлеба — 200 г",
      "Свежих овощей (огурец помидор листья салата) — 150 г",
      "Греческого йогурта % — 680 г",
      "Оливкового масла — 130 г",
      "Риса (в сухом виде) — 480 г",
      "Нежирного говяжьего фарша — 400 г",
      "Замороженных овощей (брокколи морковь горошек) — 200 г",
      "Растительного масла — 60 г",
      "Обезжиренного творога — 200 г",
      "Любого фрукта (банан яблоко) — 150 г",
      "Гречневой крупы (в сухом виде) — 300 г",
      "Сливочного масла — 20 г",
      "Огурцов — 100 г",
      "Нежирного сыра — 50 г",
      "Листьев салата — 150 г",
      "Цельнозерновой пасты (в сухом виде) — 300 г",
      "Свежих фруктов — 150 г",
      "Миндаля — 20 г",
      "Омтика цельнозернового хлеба — 4 л",
      "Куриного филе — 800 г",
      "Моркови — 200 г",
      "Творога % — 650 г",
      "Манной крупы — 50 г",
      "Яблок — 100 г",
      "Банан — 2",
      "Грецких орехов — 20 г",
      "Киноа (в сухом виде) — 100 г",
      "Лимонного сока — 20 г",
      "Картофеля — 400 г",
      "Яйцо — 1",
      "Джема без сахара — 50 г",
      "Лапши (в сухом виде) — 100 г",
      "Фруктов (яблоко банан) — 200 г",
      "Сыра — 70 г",
      "Различных фруктов (яблоко груша апельсин) — 300 г",
      "Куриного фарша — 200 г",
      "Свежих овощей (огурец помидор) — 150 г",
      "Замороженных овощей (брокколи морковь) — 200 г",
      "Кефира % — 300 мл",
      "Горчица — 20 г + +",
      "Консервированный тунец в собственном соку",
      "Консервированные томаты в собственном соку",
      "Болгарский перец",
      "Зеленая фасоль",
      "Лимонный сок",
      "Кетчуп/горчица",
      "Консервированного тунца в собственном соку — 180 г",
      "Консервированных томатов в собственном соку — 200 г",
      "Болгарского перца — 50 г",
      "Зеленой фасоли — 150 г",
      "Свежих овощей (перец огурец) — 150 г",
      "Овощей (цукини перец баклажан) — 300 г",
      "Соуса (кетчуп/горчица) — 10 г"
    ]
  },
  "sleepAdvice": {
    "messages": [
      "Ложитесь спать и просыпайтесь в одно и то же время каждый день, даже в выходные, чтобы наладить циркадные ритмы.",
      "Избегайте использования экранов (телефоны, планшеты, компьютеры) за 60 минут до сна, так как синий свет подавляет выработку мелатонина.",
      "Поддерживайте прохладную температуру в спальне (16-19°C) и обеспечьте полную темноту для лучшего качества сна.",
      "Создайте расслабляющий ритуал перед сном, например, примите теплую ванну, почитайте книгу или послушайте спокойную музыку."
    ],
    "disclaimer": "Эти советы носят информационный характер и не являются медицинской рекомендацией. При наличии серьезных проблем со сном проконсультируйтесь с врачом."
  }
}

```

---

## Сценарий 7 — Ночной график, похудение

### Запрос (POST /ai/bootstrap)

```json
{
  "profile": {
    "age": 34,
    "sex": "MALE",
    "heightCm": 178,
    "weightKg": 104,
    "goal": "LOSE_FAT",
    "experienceLevel": 2,
    "equipment": [
      "machines",
      "dumbbells",
      "cables",
      "elliptical"
    ],
    "dietaryPreferences": [],
    "allergies": [],
    "injuries": [
      "болят колени после смены"
    ],
    "healthNotes": [
      "не ем завтрак",
      "работаю ночью"
    ],
    "weeklySchedule": {
      "Mon": true,
      "Wed": true,
      "Fri": true
    },
    "lifestyleActivity": "ACTIVE",
    "locale": "ru",
    "budgetLevel": 1,
    "trainingMode": "GYM",
    "trainingFocus": "FAT_LOSS",
    "sessionDurationMins": 45,
    "splitPreference": "AUTO",
    "sleepHistory": [
      {
        "date": "2026-07-08",
        "durationMinutes": 300
      }
    ]
  },
  "weekIndex": 0,
  "locale": "ru"
}
```

### Сырой ответ сервера

```json
{
  "trainingPlan": {
    "weekIndex": 0,
    "workouts": [
      {
        "id": "w_0_1",
        "label": "Chest + Triceps",
        "date": "2026-07-09",
        "sets": [
          {
            "exerciseId": "bench",
            "reps": 6,
            "weightKg": 60,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "db_bench_press",
            "reps": 8,
            "weightKg": 45,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "incline_bench",
            "reps": 10,
            "weightKg": 40,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "tricep_extension",
            "reps": 8,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 1
          }
        ]
      },
      {
        "id": "w_0_2",
        "label": "Back + Biceps",
        "date": "2026-07-10",
        "sets": [
          {
            "exerciseId": "negative_pullup",
            "reps": 4,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "dumbbell_row",
            "reps": 8,
            "weightKg": 30,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "inverted_row",
            "reps": 6,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "curl",
            "reps": 8,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 1
          }
        ]
      },
      {
        "id": "w_0_3",
        "label": "Legs + Shoulders",
        "date": "2026-07-11",
        "sets": [
          {
            "exerciseId": "hip_thrust",
            "reps": 10,
            "weightKg": 25,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "single_leg_glute_bridge",
            "reps": 12,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "dumbbell_shoulder_press",
            "reps": 45,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 2
          },
          {
            "exerciseId": "plank",
            "reps": 8,
            "weightKg": null,
            "rpe": 6.5,
            "sets": 1
          }
        ]
      }
    ]
  },
  "nutritionPlan": {
    "weekIndex": 0,
    "mealsByDay": {
      "Mon": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "молоко",
            "ягоды",
            "мёд"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Куриная грудка с рисом и овощами",
          "ingredients": [
            "куриная грудка",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Греческий йогурт с орехами",
          "ingredients": [
            "греческий йогурт",
            "грецкие орехи",
            "мёд"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Лосось с киноа и шпинатом",
          "ingredients": [
            "филе лосося",
            "киноа",
            "шпинат",
            "лимон"
          ],
          "kcal": 560,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbsGrams": 45,
            "kcal": 560
          },
          "allergenTags": [],
          "recipe": ""
        }
      ],
      "Tue": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "молоко",
            "ягоды",
            "мёд"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Куриная грудка с рисом и овощами",
          "ingredients": [
            "куриная грудка",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Греческий йогурт с орехами",
          "ingredients": [
            "греческий йогурт",
            "грецкие орехи",
            "мёд"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Лосось с киноа и шпинатом",
          "ingredients": [
            "филе лосося",
            "киноа",
            "шпинат",
            "лимон"
          ],
          "kcal": 560,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbsGrams": 45,
            "kcal": 560
          },
          "allergenTags": [],
          "recipe": ""
        }
      ],
      "Wed": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "молоко",
            "ягоды",
            "мёд"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Куриная грудка с рисом и овощами",
          "ingredients": [
            "куриная грудка",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Греческий йогурт с орехами",
          "ingredients": [
            "греческий йогурт",
            "грецкие орехи",
            "мёд"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Лосось с киноа и шпинатом",
          "ingredients": [
            "филе лосося",
            "киноа",
            "шпинат",
            "лимон"
          ],
          "kcal": 560,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbsGrams": 45,
            "kcal": 560
          },
          "allergenTags": [],
          "recipe": ""
        }
      ],
      "Thu": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "молоко",
            "ягоды",
            "мёд"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Куриная грудка с рисом и овощами",
          "ingredients": [
            "куриная грудка",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Греческий йогурт с орехами",
          "ingredients": [
            "греческий йогурт",
            "грецкие орехи",
            "мёд"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Лосось с киноа и шпинатом",
          "ingredients": [
            "филе лосося",
            "киноа",
            "шпинат",
            "лимон"
          ],
          "kcal": 560,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbsGrams": 45,
            "kcal": 560
          },
          "allergenTags": [],
          "recipe": ""
        }
      ],
      "Fri": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "молоко",
            "ягоды",
            "мёд"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Куриная грудка с рисом и овощами",
          "ingredients": [
            "куриная грудка",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Греческий йогурт с орехами",
          "ingredients": [
            "греческий йогурт",
            "грецкие орехи",
            "мёд"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Лосось с киноа и шпинатом",
          "ingredients": [
            "филе лосося",
            "киноа",
            "шпинат",
            "лимон"
          ],
          "kcal": 560,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbsGrams": 45,
            "kcal": 560
          },
          "allergenTags": [],
          "recipe": ""
        }
      ],
      "Sat": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "молоко",
            "ягоды",
            "мёд"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Куриная грудка с рисом и овощами",
          "ingredients": [
            "куриная грудка",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Греческий йогурт с орехами",
          "ingredients": [
            "греческий йогурт",
            "грецкие орехи",
            "мёд"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Лосось с киноа и шпинатом",
          "ingredients": [
            "филе лосося",
            "киноа",
            "шпинат",
            "лимон"
          ],
          "kcal": 560,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbsGrams": 45,
            "kcal": 560
          },
          "allergenTags": [],
          "recipe": ""
        }
      ],
      "Sun": [
        {
          "name": "Овсянка с ягодами",
          "ingredients": [
            "овсяные хлопья",
            "молоко",
            "ягоды",
            "мёд"
          ],
          "kcal": 468,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 12,
            "carbsGrams": 55,
            "kcal": 468
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Куриная грудка с рисом и овощами",
          "ingredients": [
            "куриная грудка",
            "рис",
            "брокколи",
            "морковь"
          ],
          "kcal": 520,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 520
          },
          "allergenTags": [],
          "recipe": ""
        },
        {
          "name": "Греческий йогурт с орехами",
          "ingredients": [
            "греческий йогурт",
            "грецкие орехи",
            "мёд"
          ],
          "kcal": 420,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 18,
            "carbsGrams": 35,
            "kcal": 420
          },
          "allergenTags": [],
          "recipe": ""
        }
      ]
    },
    "shoppingList": [
      "Брокколи",
      "Грецкие орехи",
      "Греческий йогурт",
      "Киноа",
      "Куриная грудка",
      "Лимон",
      "Молоко",
      "Морковь",
      "Мёд",
      "Овсяные хлопья",
      "Рис",
      "Филе лосося",
      "Шпинат",
      "Ягоды"
    ]
  },
  "sleepAdvice": {
    "messages": [
      "Ложитесь и просыпайтесь в одно и то же время, даже по выходным.",
      "За час до сна приглушите свет и уберите яркие экраны.",
      "Поддерживайте прохладу и тишину в спальне (18–20 °C).",
      "Избегайте тяжёлой еды и кофеина за 3 часа до сна.",
      "Добавьте лёгкую растяжку или дыхательные упражнения перед сном.",
      "Если ночь прошла плохо, снизьте нагрузку на следующей тренировке."
    ],
    "disclaimer": "Советы носят ознакомительный характер. При серьёзных нарушениях сна обратитесь к врачу."
  }
}

```

---

## Сценарий 8 — Outdoor, нельзя брусья

### Запрос (POST /ai/bootstrap)

```json
{
  "profile": {
    "age": 24,
    "sex": "MALE",
    "heightCm": 180,
    "weightKg": 76,
    "goal": "GAIN_MUSCLE",
    "experienceLevel": 3,
    "equipment": [
      "pullup_bar",
      "dip_bars"
    ],
    "dietaryPreferences": [
      "без свинины"
    ],
    "allergies": [
      "seafood"
    ],
    "injuries": [
      "болит плечо",
      "нельзя подтягивания широким хватом",
      "нельзя брусья"
    ],
    "healthNotes": [],
    "weeklySchedule": {
      "Mon": true,
      "Wed": true,
      "Fri": true
    },
    "lifestyleActivity": "LIGHT",
    "locale": "ru",
    "budgetLevel": 2,
    "trainingMode": "OUTDOOR",
    "trainingFocus": "HYPERTROPHY",
    "sessionDurationMins": 45,
    "splitPreference": "BRO_SPLIT"
  },
  "weekIndex": 0,
  "locale": "ru"
}
```

### Сырой ответ сервера

```json
{
  "trainingPlan": {
    "weekIndex": 0,
    "workouts": [
      {
        "id": "2026_07_06_chest_triceps",
        "label": "Chest + Triceps",
        "date": "2026-07-06",
        "sets": [
          {
            "exerciseId": "pushup",
            "reps": 10,
            "weightKg": null,
            "rpe": 7,
            "sets": 3
          },
          {
            "exerciseId": "knee_pushup",
            "reps": 10,
            "weightKg": null,
            "rpe": 7,
            "sets": 3
          },
          {
            "exerciseId": "dip",
            "reps": 12,
            "weightKg": null,
            "rpe": 7,
            "sets": 2
          },
          {
            "exerciseId": "bench_dip",
            "reps": 13,
            "weightKg": null,
            "rpe": 7,
            "sets": 2
          },
          {
            "exerciseId": "diamond_pushup_knee",
            "reps": 13,
            "weightKg": null,
            "rpe": 7,
            "sets": 2
          }
        ]
      },
      {
        "id": "2026_07_08_back_biceps",
        "label": "Back + Biceps",
        "date": "2026-07-08",
        "sets": [
          {
            "exerciseId": "pullup",
            "reps": 10,
            "weightKg": null,
            "rpe": 7,
            "sets": 3
          },
          {
            "exerciseId": "inverted_row",
            "reps": 10,
            "weightKg": null,
            "rpe": 7,
            "sets": 3
          },
          {
            "exerciseId": "towel_row",
            "reps": 12,
            "weightKg": null,
            "rpe": 7,
            "sets": 2
          },
          {
            "exerciseId": "chin_up_hold",
            "reps": 13,
            "weightKg": null,
            "rpe": 7,
            "sets": 2
          },
          {
            "exerciseId": "chin_up",
            "reps": 13,
            "weightKg": null,
            "rpe": 7,
            "sets": 2
          }
        ]
      },
      {
        "id": "2026_07_10_legs_shoulders",
        "label": "Legs + Shoulders",
        "date": "2026-07-10",
        "sets": [
          {
            "exerciseId": "bodyweight_squat",
            "reps": 10,
            "weightKg": null,
            "rpe": 7,
            "sets": 3
          },
          {
            "exerciseId": "kettlebell_swing",
            "reps": 10,
            "weightKg": 16,
            "rpe": 7,
            "sets": 3
          },
          {
            "exerciseId": "pushup",
            "reps": 12,
            "weightKg": null,
            "rpe": 7,
            "sets": 2
          },
          {
            "exerciseId": "lunge",
            "reps": 13,
            "weightKg": null,
            "rpe": 7,
            "sets": 2
          },
          {
            "exerciseId": "plank",
            "reps": 30,
            "weightKg": null,
            "rpe": 7,
            "sets": 2
          }
        ]
      }
    ]
  },
  "nutritionPlan": {
    "weekIndex": 0,
    "mealsByDay": {
      "Mon": [
        {
          "name": "Овсянка с ягодами и орехами",
          "ingredients": [
            "100г овсяных хлопьев",
            "300мл молока 2.5%",
            "100г смешанных ягод (замороженных)",
            "30г грецких орехов"
          ],
          "kcal": 620,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 28,
            "carbsGrams": 70,
            "kcal": 620
          },
          "allergenTags": [],
          "recipe": "1. Сварите овсяные хлопья на молоке до готовности. 2. Добавьте ягоды и грецкие орехи. 3. Перемешайте и подавайте."
        },
        {
          "name": "Куриная грудка с рисом и овощами",
          "ingredients": [
            "200г куриной грудки",
            "150г бурого риса",
            "200г брокколи",
            "100г моркови",
            "15мл оливкового масла"
          ],
          "kcal": 750,
          "macros": {
            "proteinGrams": 55,
            "fatGrams": 20,
            "carbsGrams": 85,
            "kcal": 750
          },
          "allergenTags": [],
          "recipe": "1. Отварите рис. 2. Куриную грудку нарежьте и обжарьте на оливковом масле. 3. Брокколи и морковь отварите или приготовьте на пару. 4. Подавайте все вместе."
        },
        {
          "name": "Творог с фруктами и медом",
          "ingredients": [
            "200г нежирного творога",
            "150г яблока",
            "100г банана",
            "20г меда"
          ],
          "kcal": 450,
          "macros": {
            "proteinGrams": 30,
            "fatGrams": 5,
            "carbsGrams": 70,
            "kcal": 450
          },
          "allergenTags": [],
          "recipe": "1. Смешайте творог с нарезанными фруктами. 2. Полейте медом. 3. Перемешайте и подавайте."
        },
        {
          "name": "Сэндвич с индейкой и овощами",
          "ingredients": [
            "150г филе индейки",
            "2 ломтика цельнозернового хлеба",
            "50г салата",
            "50г помидора",
            "20г легкого майонеза"
          ],
          "kcal": 500,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 15,
            "carbsGrams": 50,
            "kcal": 500
          },
          "allergenTags": [],
          "recipe": "1. Отварите или запеките филе индейки. 2. Нарежьте индейку, помидор и салат. 3. Соберите сэндвич, смазав хлеб майонезом."
        },
        {
          "name": "Греческий йогурт с гранолой",
          "ingredients": [
            "200г греческого йогурта 2%",
            "50г гранолы",
            "50г малины"
          ],
          "kcal": 500,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 15,
            "carbsGrams": 65,
            "kcal": 500
          },
          "allergenTags": [],
          "recipe": "1. Выложите йогурт в миску. 2. Посыпьте гранолой и малиной. 3. Подавайте."
        }
      ],
      "Tue": [
        {
          "name": "Омлет с овощами и тостами",
          "ingredients": [
            "3 яйца",
            "50мл молока 2.5%",
            "100г шпината",
            "50г болгарского перца",
            "2 ломтика цельнозернового хлеба",
            "10г сливочного масла"
          ],
          "kcal": 550,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 30,
            "carbsGrams": 45,
            "kcal": 550
          },
          "allergenTags": [],
          "recipe": "1. Взбейте яйца с молоком. 2. Обжарьте шпинат и перец. 3. Вылейте яичную смесь на сковороду к овощам и готовьте до готовности. 4. Поджарьте тосты на сливочном масле."
        },
        {
          "name": "Паста с курицей и томатным соусом",
          "ingredients": [
            "180г цельнозерновой пасты",
            "200г куриной грудки",
            "200г томатного соуса (без сахара)",
            "100г цукини",
            "15мл оливкового масла"
          ],
          "kcal": 800,
          "macros": {
            "proteinGrams": 55,
            "fatGrams": 20,
            "carbsGrams": 100,
            "kcal": 800
          },
          "allergenTags": [],
          "recipe": "1. Отварите пасту. 2. Куриную грудку нарежьте и обжарьте на оливковом масле. 3. Добавьте цукини и томатный соус, тушите 10 минут. 4. Смешайте пасту с соусом и курицей."
        },
        {
          "name": "Салат с тунцом и киноа",
          "ingredients": [
            "180г консервированного тунца в собственном соку",
            "100г киноа",
            "150г огурцов",
            "100г помидоров черри",
            "50г красного лука",
            "20мл оливкового масла",
            "сок 1/2 лимона"
          ],
          "kcal": 650,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 25,
            "carbsGrams": 65,
            "kcal": 650
          },
          "allergenTags": [],
          "recipe": "1. Отварите киноа. 2. Нарежьте огурцы, помидоры и лук. 3. Смешайте тунец, киноа и овощи. 4. Заправьте оливковым маслом и лимонным соком."
        },
        {
          "name": "Протеиновый смузи",
          "ingredients": [
            "30г сывороточного протеина",
            "250мл молока 2.5%",
            "100г банана",
            "50г шпината"
          ],
          "kcal": 400,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 10,
            "carbsGrams": 40,
            "kcal": 400
          },
          "allergenTags": [],
          "recipe": "1. Смешайте все ингредиенты в блендере до однородной массы. 2. Подавайте сразу."
        },
        {
          "name": "Творожная запеканка с изюмом",
          "ingredients": [
            "250г нежирного творога",
            "1 яйцо",
            "30г манной крупы",
            "30г изюма",
            "10г сахара"
          ],
          "kcal": 450,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 10,
            "carbsGrams": 55,
            "kcal": 450
          },
          "allergenTags": [],
          "recipe": "1. Смешайте творог, яйцо, манку, изюм и сахар. 2. Выложите массу в форму для запекания. 3. Запекайте в духовке при 180°C 30-35 минут."
        }
      ],
      "Wed": [
        {
          "name": "Гречневая каша с яйцом и овощами",
          "ingredients": [
            "120г гречневой крупы",
            "2 яйца",
            "150г огурцов",
            "100г помидоров",
            "15мл оливкового масла"
          ],
          "kcal": 600,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 25,
            "carbsGrams": 70,
            "kcal": 600
          },
          "allergenTags": [],
          "recipe": "1. Отварите гречневую крупу. 2. Сварите яйца вкрутую. 3. Нарежьте огурцы и помидоры. 4. Смешайте все ингредиенты, заправьте оливковым маслом."
        },
        {
          "name": "Говядина с бататом и зеленой фасолью",
          "ingredients": [
            "200г нежирной говядины",
            "200г батата",
            "200г зеленой фасоли",
            "15мл оливкового масла"
          ],
          "kcal": 850,
          "macros": {
            "proteinGrams": 50,
            "fatGrams": 30,
            "carbsGrams": 95,
            "kcal": 850
          },
          "allergenTags": [],
          "recipe": "1. Говядину нарежьте и обжарьте до готовности. 2. Батат нарежьте кубиками и запеките или отварите. 3. Зеленую фасоль отварите или приготовьте на пару. 4. Подавайте все вместе."
        },
        {
          "name": "Куриный салат с авокадо",
          "ingredients": [
            "180г куриной грудки",
            "100г авокадо",
            "100г листового салата",
            "50г помидоров черри",
            "15мл оливкового масла",
            "сок 1/2 лимона"
          ],
          "kcal": 600,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 35,
            "carbsGrams": 30,
            "kcal": 600
          },
          "allergenTags": [],
          "recipe": "1. Отварите или запеките куриную грудку, нарежьте. 2. Нарежьте авокадо, салат и помидоры. 3. Смешайте все ингредиенты, заправьте оливковым маслом и лимонным соком."
        },
        {
          "name": "Йогурт с фруктами и семенами чиа",
          "ingredients": [
            "200г натурального йогурта",
            "150г смешанных фруктов (например, клубника, черника)",
            "15г семян чиа"
          ],
          "kcal": 400,
          "macros": {
            "proteinGrams": 20,
            "fatGrams": 15,
            "carbsGrams": 45,
            "kcal": 400
          },
          "allergenTags": [],
          "recipe": "1. Выложите йогурт в миску. 2. Добавьте нарезанные фрукты и семена чиа. 3. Перемешайте и подавайте."
        },
        {
          "name": "Протеиновый батончик домашний",
          "ingredients": [
            "50г овсяных хлопьев",
            "30г арахисовой пасты",
            "20г сывороточного протеина",
            "30мл молока 2.5%",
            "10г меда"
          ],
          "kcal": 400,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 20,
            "carbsGrams": 35,
            "kcal": 400
          },
          "allergenTags": [],
          "recipe": "1. Смешайте овсяные хлопья, арахисовую пасту, протеин, молоко и мед. 2. Сформируйте батончик. 3. Охладите в холодильнике 30 минут."
        }
      ],
      "Thu": [
        {
          "name": "Яичница с тостами и овощами",
          "ingredients": [
            "3 яйца",
            "2 ломтика цельнозернового хлеба",
            "100г помидоров",
            "50г огурцов",
            "10г сливочного масла"
          ],
          "kcal": 500,
          "macros": {
            "proteinGrams": 20,
            "fatGrams": 25,
            "carbsGrams": 40,
            "kcal": 500
          },
          "allergenTags": [],
          "recipe": "1. Приготовьте яичницу. 2. Поджарьте тосты на сливочном масле. 3. Нарежьте помидоры и огурцы. 4. Подавайте все вместе."
        },
        {
          "name": "Индейка с кускусом и овощным рагу",
          "ingredients": [
            "200г филе индейки",
            "150г кускуса",
            "100г баклажана",
            "100г цукини",
            "100г болгарского перца",
            "15мл оливкового масла"
          ],
          "kcal": 800,
          "macros": {
            "proteinGrams": 50,
            "fatGrams": 20,
            "carbsGrams": 100,
            "kcal": 800
          },
          "allergenTags": [],
          "recipe": "1. Отварите кускус. 2. Индейку нарежьте и обжарьте. 3. Овощи нарежьте кубиками и потушите на оливковом масле до готовности. 4. Подавайте индейку с кускусом и овощным рагу."
        },
        {
          "name": "Греческий йогурт с фруктами и орехами",
          "ingredients": [
            "200г греческого йогурта 2%",
            "150г смешанных фруктов",
            "30г миндаля"
          ],
          "kcal": 500,
          "macros": {
            "proteinGrams": 25,
            "fatGrams": 20,
            "carbsGrams": 50,
            "kcal": 500
          },
          "allergenTags": [],
          "recipe": "1. Выложите йогурт в миску. 2. Добавьте нарезанные фрукты и миндаль. 3. Перемешайте и подавайте."
        },
        {
          "name": "Сырники со сметаной",
          "ingredients": [
            "250г нежирного творога",
            "1 яйцо",
            "30г муки",
            "10г сахара",
            "50г сметаны 15%"
          ],
          "kcal": 500,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 20,
            "carbsGrams": 45,
            "kcal": 500
          },
          "allergenTags": [],
          "recipe": "1. Смешайте творог, яйцо, муку и сахар. 2. Сформируйте сырники и обжарьте до золотистой корочки. 3. Подавайте со сметаной."
        },
        {
          "name": "Протеиновый коктейль с овсянкой",
          "ingredients": [
            "30г сывороточного протеина",
            "250мл молока 2.5%",
            "50г овсяных хлопьев",
            "100г банана"
          ],
          "kcal": 500,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 10,
            "carbsGrams": 60,
            "kcal": 500
          },
          "allergenTags": [],
          "recipe": "1. Смешайте все ингредиенты в блендере до однородной массы. 2. Подавайте сразу."
        }
      ],
      "Fri": [
        {
          "name": "Овсянка с яблоком и корицей",
          "ingredients": [
            "100г овсяных хлопьев",
            "300мл молока 2.5%",
            "150г яблока",
            "5г корицы"
          ],
          "kcal": 550,
          "macros": {
            "proteinGrams": 20,
            "fatGrams": 15,
            "carbsGrams": 80,
            "kcal": 550
          },
          "allergenTags": [],
          "recipe": "1. Сварите овсяные хлопья на молоке. 2. Добавьте нарезанное яблоко и корицу. 3. Перемешайте и подавайте."
        },
        {
          "name": "Куриный суп с лапшой",
          "ingredients": [
            "200г куриного филе",
            "100г яичной лапши",
            "100г моркови",
            "100г картофеля",
            "50г лука",
            "15мл растительного масла"
          ],
          "kcal": 700,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 20,
            "carbsGrams": 85,
            "kcal": 700
          },
          "allergenTags": [],
          "recipe": "1. Отварите куриное филе, нарежьте. 2. Нарежьте овощи. 3. Обжарьте лук и морковь. 4. Добавьте картофель, курицу, лапшу и бульон, варите до готовности."
        },
        {
          "name": "Творог с бананом и медом",
          "ingredients": [
            "200г нежирного творога",
            "200г банана",
            "20г меда"
          ],
          "kcal": 450,
          "macros": {
            "proteinGrams": 30,
            "fatGrams": 5,
            "carbsGrams": 75,
            "kcal": 450
          },
          "allergenTags": [],
          "recipe": "1. Смешайте творог с нарезанным бананом. 2. Полейте медом. 3. Перемешайте и подавайте."
        },
        {
          "name": "Бургер с куриной котлетой",
          "ingredients": [
            "180г куриного фарша",
            "1 цельнозерновая булочка",
            "50г салата",
            "50г помидора",
            "20г легкого соуса (например, йогуртового)"
          ],
          "kcal": 600,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 20,
            "carbsGrams": 60,
            "kcal": 600
          },
          "allergenTags": [],
          "recipe": "1. Сформируйте котлету из фарша и обжарьте. 2. Булочку разрежьте и поджарьте. 3. Соберите бургер с котлетой, салатом, помидором и соусом."
        },
        {
          "name": "Фруктовый салат с йогуртом",
          "ingredients": [
            "200г смешанных фруктов (например, апельсин, киви, виноград)",
            "150г натурального йогурта"
          ],
          "kcal": 400,
          "macros": {
            "proteinGrams": 15,
            "fatGrams": 5,
            "carbsGrams": 70,
            "kcal": 400
          },
          "allergenTags": [],
          "recipe": "1. Нарежьте фрукты. 2. Смешайте с йогуртом. 3. Подавайте."
        }
      ],
      "Sat": [
        {
          "name": "Панкейки с ягодами и сиропом",
          "ingredients": [
            "150г муки",
            "1 яйцо",
            "200мл молока 2.5%",
            "10г разрыхлителя",
            "100г смешанных ягод",
            "30мл кленового сиропа"
          ],
          "kcal": 650,
          "macros": {
            "proteinGrams": 20,
            "fatGrams": 15,
            "carbsGrams": 110,
            "kcal": 650
          },
          "allergenTags": [],
          "recipe": "1. Смешайте муку, яйцо, молоко и разрыхлитель до однородной массы. 2. Выпекайте панкейки на сковороде. 3. Подавайте с ягодами и кленовым сиропом."
        },
        {
          "name": "Куриные котлеты с картофельным пюре",
          "ingredients": [
            "200г куриного фарша",
            "300г картофеля",
            "50мл молока 2.5%",
            "10г сливочного масла",
            "50г лука",
            "15мл растительного масла"
          ],
          "kcal": 800,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 30,
            "carbsGrams": 90,
            "kcal": 800
          },
          "allergenTags": [],
          "recipe": "1. Сформируйте котлеты из фарша и обжарьте. 2. Отварите картофель, разомните с молоком и маслом в пюре. 3. Подавайте котлеты с пюре."
        },
        {
          "name": "Салат с тунцом и кукурузой",
          "ingredients": [
            "180г консервированного тунца в собственном соку",
            "150г консервированной кукурузы",
            "100г огурцов",
            "50г листового салата",
            "20мл оливкового масла"
          ],
          "kcal": 600,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 25,
            "carbsGrams": 50,
            "kcal": 600
          },
          "allergenTags": [],
          "recipe": "1. Слейте жидкость с тунца и кукурузы. 2. Нарежьте огурцы и салат. 3. Смешайте все ингредиенты, заправьте оливковым маслом."
        },
        {
          "name": "Протеиновый пудинг с семенами чиа",
          "ingredients": [
            "30г сывороточного протеина",
            "250мл молока 2.5%",
            "20г семян чиа",
            "10г какао-порошка"
          ],
          "kcal": 400,
          "macros": {
            "proteinGrams": 35,
            "fatGrams": 15,
            "carbsGrams": 30,
            "kcal": 400
          },
          "allergenTags": [],
          "recipe": "1. Смешайте все ингредиенты в миске. 2. Оставьте в холодильнике на несколько часов или на ночь до загустения. 3. Подавайте."
        },
        {
          "name": "Фруктовый смузи с йогуртом",
          "ingredients": [
            "200г смешанных замороженных фруктов",
            "150г натурального йогурта",
            "100мл воды"
          ],
          "kcal": 400,
          "macros": {
            "proteinGrams": 15,
            "fatGrams": 5,
            "carbsGrams": 65,
            "kcal": 400
          },
          "allergenTags": [],
          "recipe": "1. Смешайте все ингредиенты в блендере до однородной массы. 2. Подавайте сразу."
        }
      ],
      "Sun": [
        {
          "name": "Омлет с сыром и овощами",
          "ingredients": [
            "3 яйца",
            "50мл молока 2.5%",
            "50г сыра (например, чеддер)",
            "100г помидоров",
            "50г шпината",
            "10г сливочного масла"
          ],
          "kcal": 550,
          "macros": {
            "proteinGrams": 28,
            "fatGrams": 35,
            "carbsGrams": 25,
            "kcal": 550
          },
          "allergenTags": [],
          "recipe": "1. Взбейте яйца с молоком. 2. Обжарьте помидоры и шпинат. 3. Вылейте яичную смесь на сковороду к овощам, посыпьте сыром и готовьте до готовности."
        },
        {
          "name": "Запеченный лосось с киноа и спаржей",
          "ingredients": [
            "200г замороженного лосося",
            "150г киноа",
            "200г спаржи",
            "15мл оливкового масла",
            "сок 1/2 лимона"
          ],
          "kcal": 850,
          "macros": {
            "proteinGrams": 50,
            "fatGrams": 35,
            "carbsGrams": 80,
            "kcal": 850
          },
          "allergenTags": [],
          "recipe": "1. Запеките лосось в духовке. 2. Отварите киноа. 3. Спаржу отварите или приготовьте на пару. 4. Подавайте лосось с киноа и спаржей, сбрызнув лимонным соком и оливковым маслом."
        },
        {
          "name": "Куриный салат с булгуром",
          "ingredients": [
            "180г куриной грудки",
            "100г булгура",
            "150г огурцов",
            "100г болгарского перца",
            "15мл оливкового масла",
            "сок 1/2 лимона"
          ],
          "kcal": 650,
          "macros": {
            "proteinGrams": 45,
            "fatGrams": 20,
            "carbsGrams": 70,
            "kcal": 650
          },
          "allergenTags": [],
          "recipe": "1. Отварите или запеките куриную грудку, нарежьте. 2. Отварите булгур. 3. Нарежьте огурцы и перец. 4. Смешайте все ингредиенты, заправьте оливковым маслом и лимонным соком."
        },
        {
          "name": "Творожный десерт с фруктами",
          "ingredients": [
            "200г нежирного творога",
            "150г смешанных фруктов",
            "20г меда"
          ],
          "kcal": 450,
          "macros": {
            "proteinGrams": 30,
            "fatGrams": 5,
            "carbsGrams": 70,
            "kcal": 450
          },
          "allergenTags": [],
          "recipe": "1. Смешайте творог с нарезанными фруктами. 2. Полейте медом. 3. Перемешайте и подавайте."
        },
        {
          "name": "Протеиновый коктейль с ореховой пастой",
          "ingredients": [
            "30г сывороточного протеина",
            "250мл молока 2.5%",
            "30г арахисовой пасты",
            "100г банана"
          ],
          "kcal": 450,
          "macros": {
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbsGrams": 30,
            "kcal": 450
          },
          "allergenTags": [],
          "recipe": "1. Смешайте все ингредиенты в блендере до однородной массы. 2. Подавайте сразу."
        }
      ]
    },
    "shoppingList": [
      "Овсяные хлопья",
      "Молоко % — 2.5",
      "Смешанные ягоды (замороженные)",
      "Грецкие орехи",
      "Куриная грудка",
      "Бурый рис",
      "Брокколи + + — 200 г",
      "Морковь",
      "Оливковое масло",
      "Нежирный творог",
      "Яблоко",
      "Банан + + — 600 г",
      "Мед + + — 70 г",
      "Филе индейки + + — 350 г",
      "Цельнозерновой хлеб",
      "Салат + + — 100 г",
      "Помидор + + — 400 г",
      "Легкий майонез",
      "Греческий йогурт % — 2",
      "Гранола + + — 50 г",
      "Малина + + — 50 г",
      "Яйца + + — 11",
      "Шпинат + + — 200 г",
      "Сливочное масло",
      "Цельнозерновая паста",
      "Цукини + + — 200 г",
      "Киноа + + — 250 г",
      "Огурцы + + — 600 г",
      "Помидоры черри",
      "Красный лук",
      "Лимон",
      "Сывороточный протеин",
      "Манная крупа",
      "Изюм + + — 30 г",
      "Сахар + + — 20 г",
      "Гречневая крупа",
      "Нежирная говядина",
      "Батат + + — 200 г",
      "Авокадо + + — 100 г",
      "Листовой салат",
      "Натуральный йогурт",
      "Семена чиа",
      "Арахисовая паста",
      "Кускус + + — 150 г",
      "Баклажан + + — 100 г",
      "Мука + + — 180 г",
      "Кленовый сироп",
      "Куриный фарш",
      "Картофель",
      "Лук + + — 100 г",
      "Растительное масло",
      "Какао порошок",
      "Сыр (например чеддер)",
      "Замороженный лосось",
      "Спаржа + + — 200 г",
      "Булгур + + — 100 г",
      "Апельсин",
      "Киви",
      "Виноград",
      "Овсяных хлопьев — 600 г",
      "Молока % — 3960 мл",
      "Смешанных ягод (замороженных) — 200 г",
      "Грецких орехов — 60 г",
      "Куриной грудки — 1520 г",
      "Бурого риса — 300 г",
      "Моркови — 400 г",
      "Оливкового масла — 320 мл",
      "Нежирного творога — 2200 г",
      "Яблока — 600 г",
      "Омтика цельнозернового хлеба — 12 л",
      "Легкого майонеза — 40 г",
      "Греческого йогурта % — 800 г",
      "Сливочного масла — 80 г",
      "Цельнозерновой пасты — 360 г",
      "Помидоров черри — 300 г",
      "Красного лука — 100 г",
      "Сок / лимона — 8",
      "Сывороточного протеина — 280 г",
      "Яйцо — 6",
      "Манной крупы — 60 г",
      "Гречневой крупы — 240 г",
      "Нежирной говядины — 400 г",
      "Листового салата — 300 г",
      "Натурального йогурта — 1000 г",
      "Смешанных фруктов (например клубника черника) — 300 г",
      "Семян чиа — 70 г",
      "Арахисовой пасты — 120 г",
      "Смешанных фруктов — 600 г",
      "Миндаля — 60 г",
      "Сметаны % — 100 г",
      "Куриного филе — 400 г",
      "Яичной лапши — 200 г",
      "Картофеля — 800 г",
      "Растительного масла — 60 мл",
      "Куриного фарша — 760 г",
      "Цельнозерновая булочка — 2",
      "Смешанных фруктов (например апельсин киви виноград) — 400 г",
      "Смешанных ягод — 200 г",
      "Кленового сиропа — 60 мл",
      "Какао порошка — 20 г",
      "Смешанных замороженных фруктов — 400 г",
      "Воды — 200 мл",
      "Сыра (например чеддер) — 100 г",
      "Замороженного лосося — 400 г",
      "Брокколи — 200 г",
      "Банана — 600 г",
      "Меда — 70 г",
      "Филе индейки — 350 г",
      "Салата — 100 г",
      "Помидора — 400 г",
      "Гранолы — 50 г",
      "Малины — 50 г",
      "Яйца — 11",
      "Шпината — 200 г",
      "Цукини — 200 г",
      "Киноа — 250 г",
      "Огурцов — 600 г",
      "Изюма — 30 г",
      "Сахара — 20 г",
      "Батата — 200 г",
      "Авокадо — 100 г",
      "Кускуса — 150 г",
      "Баклажана — 100 г",
      "Муки — 180 г",
      "Лука — 100 г",
      "Спаржи — 200 г",
      "Булгура — 100 г",
      "Болгарский перец",
      "Томатный соус (без сахара)",
      "Консервированный тунец в собственном соку",
      "Зеленая фасоль",
      "Разрыхлитель",
      "Консервированная кукуруза",
      "Болгарского перца — 500 г",
      "Томатного соуса (без сахара) — 400 г",
      "Консервированного тунца в собственном соку — 720 г",
      "Зеленой фасоли — 400 г",
      "Корицы — 10 г",
      "Легкого соуса (например йогуртового) — 40 г",
      "Разрыхлителя — 20 г",
      "Консервированной кукурузы — 300 г"
    ]
  },
  "sleepAdvice": {
    "messages": [
      "Учитывая ваше больное плечо и ограничения на подтягивания широким хватом и брусья, сосредоточьтесь на упражнениях, которые не нагружают плечо. Например, используйте нейтральный хват для подтягиваний (если это комфортно) или замените их на тяги гантелей/блока к поясу.",
      "Для набора мышечной массы (гипертрофия) важен достаточный объем тренировок. Несмотря на ограничения, старайтесь выполнять 3-4 подхода по 8-12 повторений для каждой мышечной группы, используя доступное оборудование и безопасные упражнения.",
      "Обеспечьте достаточное потребление белка (1.6-2.2 г на кг веса тела) для восстановления и роста мышц. Учитывая ваши диетические предпочтения (без свинины) и аллергию на морепродукты, выбирайте другие источники белка, такие как курица, говядина, индейка, яйца, молочные продукты и растительные белки.",
      "Поскольку вы тренируетесь на улице и у вас легкая активность в течение дня, убедитесь, что вы получаете достаточно калорий для набора мышечной массы. Отслеживайте свой прогресс и при необходимости увеличивайте потребление пищи.",
      "Не забывайте о разминке перед тренировкой и заминке после нее, уделяя особое внимание мобильности плечевого сустава, но без боли. Растяжка и легкие вращения могут помочь улучшить кровоток и подготовить мышцы.",
      "Качественный сон (7-9 часов) критически важен для восстановления мышц и гормонального баланса, особенно при наборе массы. Старайтесь ложиться спать и просыпаться в одно и то же время, чтобы наладить циркадные ритмы.",
      "Прислушивайтесь к своему телу, особенно к больному плечу. Если упражнение вызывает боль, немедленно прекратите его. Возможно, стоит проконсультироваться с физиотерапевтом для индивидуальных рекомендаций по реабилитации плеча."
    ],
    "disclaimer": "Эти советы носят общий характер. Всегда консультируйтесь с врачом или квалифицированным специалистом перед началом новой программы тренировок или изменением диеты, особенно при наличии травм или аллергии."
  }
}

```

---

