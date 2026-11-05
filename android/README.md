# برنامه هواشناسی امین (اندروید)

تبدیل داشبورد وب پیش‌بینی هواشناسی به یک برنامه اندرویدی با WebView.
منطق کامل محاسباتی (ensemble، همرفتی، رهگیری، بازه‌ها) به **Kotlin** منتقل شده است — اپ بدون نیاز به سرور پایتون مستقل و زنده است.

## ساختار
```
android/
├── settings.gradle.kts          # تنظیمات Gradle
├── build.gradle.kts             # پلاگین‌های سراسری
├── gradle.properties
└── app/
    ├── build.gradle.kts         # وابستگی‌های اپ
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/amin/weather/
        │   ├── MainActivity.kt      # WebView + دکمه به‌روزرسانی زنده
        │   ├── WeatherBridge.kt     # پل Kotlin ↔ جاوااسکریپت
        │   └── core/
        │       ├── OpenMeteoClient.kt  # واکشی ۴ مدل + احتمال ensemble
        │       └── ForecastEngine.kt   # موتور کامل پیش‌بینی (Kotlin)
        ├── assets/dashboard/        # داشبورد وب (index.html / style.css / data.js)
        └── res/                     # layout / values / drawable
```

## موتور محاسباتی (Kotlin)
`ForecastEngine` منطق پایتون (`weather/`) را به‌طور کامل بازتولید می‌کند:
- **ensemble:** وزن‌دهی هوشمند، میانه، اصلاح بایاس بارش (ICON) و دما (MOS)
- **همرفتی:** سطح ناپایداری، امتیاز صاعقه، شاخص شدید، نوع ابر، برش باد
- **رههگیری:** فازها (آرام/توسعه/اوج) و استخراج الگو
- **بازه‌ها:** ساعتی / ۶ساعته / ۱۲ساعته / روزانه

## بیلد
1. پروژه را در **Android Studio** باز کنید (`android/`).
2. بگذارید Gradle sync شود (Gradle wrapper 8.7 همراه پروژه است).
3. `Run` → یک دستگاه/شبیه‌ساز اندرویدی (minSdk 24).
4. برای APK: `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`.
5. از خط فرمان: `./gradlew assembleDebug` (در `android/`).

## تأیید کد (انجام‌شده)
- موتور Kotlin (`core/`) با **kotlinc** کامپایل و خطاهای نحوی/نوعی برطرف شد.
- تست اجرایی روی `computeWeights` و `buildEnsemble` انجام شد: تجمیع همرفتی‌آگاه بارش (میانه + نیمِ بیشینه) و اصلاح بایاس ICON تأیید شد.
- فایل‌های `core/` فقط به `org.json` و `java.net` وابسته‌اند (بدون Android) — برای تست مستقل قابل کامپایل‌اند.

## داده
- **آفلاین:** داشبورد با `data.js` باندل‌شده بلافاصله کار می‌کند.
- **زنده:** دکمه «🔄 به‌روزرسانی» (بالا-راست) `ForecastEngine` را در Kotlin اجرا می‌کند، نتیجه را در `localStorage` ذخیره و صفحه را دوباره بارگذاری می‌کند — بدون سرور.
- داشبورد هنگام بارگذاری، اگر `localStorage.fc_data` موجود باشد آن را ترجیح می‌دهد (قلاب در `index.html`).

## نکته
این پروژه در ساندباکس (بدون Android SDK) تولید شده و در اینجا بیلد نشده است؛ در Android Studio بیلد می‌شود.
