# Recording the demo

The video is built by `demo/build_demo.py` from `demo/scenes.json`. The
narration, subtitles, title cards, numbers, the code card and the ending are
already done. What it needs from a real phone is **four screen recordings**.
Until they exist, their slots show a placeholder saying what to record.

```
pip install kokoro-onnx soundfile imageio-ffmpeg pillow numpy fonttools brotli
python demo/build_demo.py                      # -> demo/out/dewey-demo.mp4 (+ .srt)
python demo/build_demo.py --only 06-revenuecat # rebuild one scene to check a clip
python demo/build_demo.py --voice am_michael   # a different voice (af_heart is the default)
```

The first run downloads the Kokoro voice model (about 350MB) and the fonts into
`demo/.cache/`. After that, narration is cached per sentence, so a rebuild only
re-synthesises lines you changed.

## Before recording

1. Build and install the `demo` variant (it carries the Test Store key):
   `./gradlew installDemo`, or the APK for your ABI from `app/build/outputs/apk/demo/`.
2. Put the test corpus on the device, in a subfolder of Download (Android will
   not grant the Download root itself):
   ```
   python tools/corpus/generate_corpus.py
   adb shell mkdir -p /sdcard/Download/Archive
   adb push tools/corpus/corpus/. /sdcard/Download/Archive/
   ```
3. Use the **dark theme** (Me → Appearance). The video's frame is dark, and
   a light phone screen inside it looks like a hole.
4. Clean status bar:
   ```
   adb shell settings put global sysui_demo_allowed 1
   adb shell am broadcast -a com.android.systemui.demo -e command enter
   adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
   adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
   ```
5. For the RevenueCat scene, start **un-purchased**: clear app data
   (`adb shell pm clear app.dewey`), or use a Test Store customer that has
   never bought.
6. Don't fire test questions at the assistant right before recording. Gemini's
   free tier rate-limits after about 40 requests in a few minutes.

Record each clip with `adb shell screenrecord --bit-rate 12000000 /sdcard/03-sort.mp4`
(3-minute limit per clip), then `adb pull /sdcard/03-sort.mp4 demo/footage/`.
The emulator's own record button works too. Any portrait resolution is fine;
the builder scales it.

## The four clips

Each clip is fitted to its narration: sped up to 2.5x if it runs long, or held on
its last frame if it runs short. So record at a natural pace and don't rush.
Aim for about the narration length, or up to twice it.

| File | Narration | What to do on screen |
|---|---|---|
| `03-sort.mp4` | ~24s | Documents tab, with the `Archive` folder linked (link it before recording; indexing 100 files takes a while). Tap **Sort**. Let the progress run, then scroll the new folders (bills, statements, contracts...) and open the review queue. End on the **Undo** button. |
| `05-ask.mp4` | ~14s | Open the assistant. Type *When is my Lydec bill due and how much is it?*, wait for the answer and its source chips, tap one. Then briefly swipe through the Bills tab. |
| `06-revenuecat.mp4` | ~31s | Tap the locked Documents tab, which shows the paywall with live prices. Pick **Annual** and buy through the Test Store dialog, landing on the unlocked tab. Then open the Me tab: **sign in** with an email (the account card), then tap **Help with my subscription** to show the Customer Center. |
| `08-tools.mp4` | ~14s | Home → **Scan**, capture a page. Then **Reorder**: long-press a page thumbnail and drag it. Then **Sign**: draw a signature and drop it on a page. |

Optional: `01-hook` is rendered from the corpus's real file names. To use a real
Files-app recording of the folder instead, change that scene's `kind` to
`phone` and give it a `footage` file like the others.

## Length

The cut runs about **2:00**. Everything the judges score (the problem, the sort,
the assistant and the whole RevenueCat flow) is over by 1:40. The code card
and tools come after. Edit `sentences` in `scenes.json` to change a line; the
timing follows the words.
