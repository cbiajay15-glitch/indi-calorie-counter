# Indi Calorie Counter

A simple calorie counter for Indian food. No ads, no account, no AI. Data stays in the phone's browser.

## Features
- Daily calorie target from age, height, weight, activity and goal (Mifflin–St Jeor)
- 370 vegetarian foods in home servings (katori, roti, plate, cup), searchable in English or Hindi (e.g. "kela", "dudh", "shakkar")
- Breakfast / Lunch / Snacks / Dinner logging with half-serving steps
- "My food" for your own recipes or packaged food, plus "Quick kcal"
- Protein, carbs and fat bars, water glasses, past days
- Weight log with trend chart
- Activity: steps, walk / run (distance, time, incline), gym, and 14 other activities, with "my routine" and "same as yesterday"
- Eaten vs burned for every day, a 7-day deficit chart, and a Reality check that compares the burn estimate with your weight trend
- Backup export/import (.json), dark mode, works offline once installed

## Run it
It is a static web app: index.html, foods.js, sw.js, manifest.webmanifest, icon.svg.
Host the folder on any HTTPS static host (GitHub Pages, Netlify, Cloudflare Pages), open it in Chrome on
Android and choose "Add to Home screen". Offline mode needs HTTPS.

Locally: `python3 -m http.server 8000` in this folder, then open http://localhost:8000.

## Food data
Dish values are calculated, not copied: each dish has a per-serving recipe in `data/recipes.py`, and ingredient
values come from USDA FoodData Central (SR Legacy 2018-04 + Foundation Foods 2025-04), which is public domain (CC0).
Some Indian ingredients have no exact USDA entry and use the closest equivalent (paneer = queso blanco,
ragi/bajra = millet, poha = white rice, jaggery = brown sugar, etc.); every substitution is marked PROXY in
`data/ingredients.py`. Rebuild `foods.js` after editing recipes with `python3 data/build.py`.

## Limits
- Recipes and portions vary from home to home, so values are standard estimates, not lab measurements.
- Data is stored only in that browser. Clearing site data deletes it. Use Settings → Export backup.

## How calories burned are estimated
- Base: BMR (Mifflin–St Jeor, from age, sex, height and latest weight) + non-walking daily life by work type
  (desk 10%, on your feet 15%, physical work 30% of BMR) + 10% of food eaten for digestion.
- Walk and run: ACSM metabolic equations from distance, time and incline (net of resting). Level walking works out to
  about 0.5 kcal per kg per km and running about 0.9.
- Gym and other activities: Compendium of Physical Activities MET values, net of resting.
- Steps: every step at walking cost. Steps already covered by a logged walk, run, gym or sport session are subtracted
  from the watch total, so nothing is counted twice. With no step count, an everyday amount for the work type is assumed.
- Exercise is never added back to the food target. The Reality check (Weight tab) needs 10 fully logged days and
  4 weigh-ins over 14+ days; it can then correct the burn estimate to match what the weight actually did.

