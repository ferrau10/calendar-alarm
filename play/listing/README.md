# Google Play listing

Everything to paste/upload in Play Console → **Grow users → Store presence → Main store listing**.

| Field | File | Limit |
|---|---|---|
| App name | `en-US/title.txt` | 30 |
| Short description | `en-US/short_description.txt` | 80 |
| Full description | `en-US/full_description.txt` | 4000 |
| App icon (512×512) | `en-US/graphics/icon-512.png` | |
| Feature graphic (1024×500) | `en-US/graphics/feature-graphic-1024x500.png` | |
| Phone screenshots (1080×1920) | `en-US/phone-screenshots/*.png` | 2–8 |

Other fields: category **Productivity**, contact email (yours), website
`https://ferrau10.github.io/calendar-alarm/`, privacy policy
`https://ferrau10.github.io/calendar-alarm/privacy.html`.

The screenshots are rendered from the real screens with sample data. Regenerate after UI changes:

```bash
./gradlew testDebugUnitTest --tests '*StoreScreenshots*' -Pscreenshots=true
```
