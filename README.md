# TelegramDrive (Kotlin / Android)

গুগল ড্রাইভের মতো একটা অ্যাপ, যা ডাটা স্টোর করে Telegram-এর একটা প্রাইভেট চ্যানেলে —
Bot API না, বরং **MTProto (TDLib)** ব্যবহার করে ইউজারের নিজের অ্যাকাউন্ট দিয়ে।

## এই স্কেলিটনে যা আছে

- Jetpack Compose UI (Login → File list → Upload flow)
- `TelegramClient.kt` — TDLib-এর callback API-কে Kotlin coroutines/Flow দিয়ে wrap করা
- Room ডাটাবেস — লোকাল ফাইল ইনডেক্স/ক্যাশ
- Repository লেয়ার — TDLib + Room-কে একসাথে জোড়া দেয়
- অথেন্টিকেশন ফ্লো (phone → OTP → 2FA) এবং প্রাইভেট চ্যানেল অটো-ক্রিয়েশন লজিক

## যা আপনাকে নিজে করতে হবে (গুরুত্বপূর্ণ)

TDLib একটা C++ লাইব্রেরি — এটা কোনো পাবলিক Maven আর্টিফ্যাক্ট হিসেবে অফিসিয়ালি পাওয়া যায় না,
তাই নিচের যেকোনো একটা পথে যেতে হবে:

### অপশন A — নিজে বিল্ড করুন (রিকমেন্ডেড, official)
1. https://github.com/tdlib/td থেকে সোর্স ক্লোন করুন
2. Android NDK দিয়ে বিল্ড করুন (repo-র `example/android` ফোল্ডারে বিস্তারিত স্ক্রিপ্ট আছে)
3. আউটপুট `.so` ফাইলগুলো (প্রতিটা ABI-র জন্য) এই প্রজেক্টের `app/src/main/jniLibs/<abi>/` এ রাখুন
4. `TdApi.java` ও JNI বাইন্ডিং ক্লাসগুলো `app/src/main/java/` এ কপি করুন (td repo বিল্ডের সময় জেনারেট হয়)

### অপশন B — কমিউনিটি প্রিবিল্ট ব্যবহার করুন
কিছু কমিউনিটি মেইনটেইনড AAR/wrapper আছে (যেমন Kotlogram-এর মতো প্রজেক্ট), কিন্তু এগুলো
আপ-টু-ডেট নাও থাকতে পারে — production-এ ব্যবহারের আগে যাচাই করে নেবেন।

### api_id / api_hash
1. https://my.telegram.org এ গিয়ে লগইন করুন
2. "API development tools" এ গিয়ে একটা নতুন অ্যাপ রেজিস্টার করুন
3. পাওয়া `api_id` ও `api_hash` → `TelegramClient.kt` এর `API_ID` / `API_HASH` কনস্ট্যান্টে বসান
   (production-এ এগুলো hardcode না করে local.properties/BuildConfig দিয়ে ইনজেক্ট করবেন)

## api_id / api_hash কোথায় বসাবেন (নিরাপদভাবে)

কখনো `TelegramClient.kt`-এ সরাসরি hardcode করবেন না, বিশেষ করে রিপো public হলে। দুটো পথ:

**লোকাল বিল্ড (Android Studio):**
`local.properties.example` কপি করে `local.properties` নামে রাখুন (এটা `.gitignore`-এ আছে, push হবে না), তারপর:
```
tg.api.id=YOUR_API_ID_NUMBER
tg.api.hash=YOUR_API_HASH_STRING
```

**GitHub Actions দিয়ে APK বিল্ড:**
রিপোর **Settings → Secrets and variables → Actions** এ গিয়ে দুটো secret যোগ করুন:
- `TG_API_ID`
- `TG_API_HASH`

তারপর **Actions** ট্যাব থেকে **Build APK** workflow রান করুন — এটা `app/build/outputs/apk/debug/` থেকে একটা debug APK বানিয়ে artifact হিসেবে দেবে, যা সরাসরি ফোনে ইনস্টল করা যাবে। Secrets GitHub-এ এনক্রিপ্টেড থাকে, লগেও প্লেইনটেক্সটে দেখা যায় না।

## রান করার আগে
- `local.properties` এ আপনার Android SDK path ঠিক আছে কিনা দেখুন
- Android Studio দিয়ে প্রজেক্ট খুলে Gradle sync করুন
- `jniLibs` ফোল্ডারে TDLib `.so` ফাইল যোগ করার পরই অ্যাপ বিল্ড হবে (এখন এই ফাইল ছাড়া কম্পাইল
  এরর দেবে `TdApi`/`Client` ক্লাস না পাওয়ার কারণে — এটাই একমাত্র বাইরের ডিপেন্ডেন্সি)

## পরের ধাপ (roadmap)
1. TDLib native lib ইন্টিগ্রেট করা
2. Auth flow টেস্ট করা (phone → OTP)
3. প্রাইভেট চ্যানেল অটো-ক্রিয়েশন টেস্ট করা
4. ফাইল আপলোড/ডাউনলোড + progress tracking
5. ফোল্ডার সিস্টেম (caption-based virtual folders)
6. অটো-ব্যাকআপ (WorkManager দিয়ে DCIM মনিটর)
