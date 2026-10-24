package io.github.newsticker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Single-page iOS-style settings (inset grouped cards on black, hairline separators,
 * a real toggle switch): left column holds the bar switch, visibility, actions and the
 * weight donut; the right column is a compact grid of all topics. Topic interactions:
 * OK toggles a topic, long-OK enters weight edit (up/down moves the weight, OK confirms).
 * Everything is built in code (no libraries) and driven by the D-pad.
 */
public final class MainActivity extends Activity {
    // iOS dark palette
    private static final int BG = 0xFF000000;
    private static final int CARD = 0xFF1C1C1E;
    private static final int CARD_HI = 0xFF2C2C2E;
    private static final int SEP = 0xFF38383A;
    private static final int LABEL = 0xFF8E8E93;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int GREEN = 0xFF30D158;
    private static final int RED = 0xFFFF453A;
    private static final int ACCENT = 0xFFFFC93C;      // ticker amber, used for focus/buttons

    /** Stable color per feed, by catalog order (same colors drive the pie and the dots). */
    private static final int[] PALETTE = {
            0xFFEF5350, 0xFF42A5F5, 0xFF26A69A, 0xFFAB47BC, 0xFFFFA726,
            0xFF66BB6A, 0xFFEC407A, 0xFF5C6BC0, 0xFF26C6DA, 0xFFFF7043,
            0xFF9CCC65, 0xFF7E57C2, 0xFFFFD54F, 0xFF4DD0E1, 0xFF8D6E63,
            0xFFF06292, 0xFF7986CB, 0xFFAED581, 0xFFFF8A65, 0xFF4FC3F7};

    private final Handler ui = new Handler();
    private Runnable pendingRefresh;

    private TextView pill;
    private SwitchView powerSwitch;
    private TextView visValue;
    private TextView linesValue;
    private TextView styleValue;
    private PieChartView pie;
    private LinearLayout topicsCard;
    private TextView hint;
    private final List<LinearLayout> cells = new ArrayList<>();
    private final List<LinearLayout> leftRows = new ArrayList<>();
    private LinearLayout editingCell;

    // ---------- category editor & keyboard ----------
    private List<Feed> catalog = new ArrayList<>();
    private LinearLayout editorPanel, listContainer, formContainer, keyboardPanel;
    private LinearLayout nomeRow, queryRow;
    private TextView editorTitle, editorNote, tipoVal, nomeVal, queryVal, keyTitle, keyPreview;
    private TextView deleteBtn;
    private Topic editingTopic;
    private int editType, slugIdx = 1;
    private ScrollView mainScroll;
    private final StringBuilder nameBuf = new StringBuilder();
    private final StringBuilder queryBuf = new StringBuilder();
    private StringBuilder keyBuf;
    private TextView keyPreviewTarget;
    private View keyReturnRow;
    private Runnable keyOnClose;
    private enum KeyKind { LETTER, CHAR, FUNC, RETURN, SHIFT, PAGE, SPACE, OK }
    private final java.util.HashMap<TextView, KeyKind> keyKinds = new java.util.HashMap<>();
    private View firstKey;
    private LinearLayout tipoRow;
    private final List<LinearLayout> listRows = new ArrayList<>();
    private boolean caps = true;
    private TextView capsKey;
    private final List<TextView> letterKeyViews = new ArrayList<>();
    private LinearLayout rssBox;

    // keyboard state (layout italiano, sfondo nero)
    private boolean shiftOn, capsLock, numPage;
    private final List<TextView> letterKeys = new ArrayList<>();
    private TextView shiftKey;
    private LinearLayout keysBox;

    // goliardia editor
    private LinearLayout goliContainer;
    private final List<String> goliItems = new ArrayList<>();
    private int goliEditIdx = -1;
    private final List<LinearLayout> goliRows = new ArrayList<>();

    private static final String[] TYPE_LABELS = {"Google News", "Sezione ANSA", "RSS", "Goliardia"};
    private static final String[] TYPE_KEYS = {Topic.GNEWS, Topic.ANSA, Topic.RSS, Topic.GOLIARDIA};
    private static final String[] QUERY_LABELS =
            {"Termini di ricerca", "Sezione ANSA", "URL del feed", "Notizie inventate"};
    private static final String[] SLUGS =
            {"topnews", "cronaca", "politica", "economia", "mondo", "sport", "cultura", "scienza"};
    private static final String[] GOLI_SAMPLES = {
            "Svincolato il fantasma dello stadio: nessun club ha voglia di prenderlo",
            "Tifo organizzato in biblioteca: 300 studenti zitti per 8 ore",
            "Il robot aspirapolvere del dipartimento si candida al senato accademico",
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        catalog = Prefs.allFeeds(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(14);
        // bottom padding clears the overlay bar in both modes (34dp single, 58dp double)
        root.setPadding(p, dp(8), p, dp(58));
        scroll.addView(root, matchWrap());

        root.addView(header(), matchWrap());

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(columns, matchWrap());

        columns.addView(leftPane());
        columns.addView(rightPane());

        FrameLayout frame = new FrameLayout(this);
        mainScroll = scroll;
        frame.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(buildEditorPanel(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(buildKeyboardPanel(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(frame);
        syncAll();
        refreshRssDirectory();
        // always open with focus in the settings column
        scroll.post(() -> {
            if (!leftRows.isEmpty()) leftRows.get(0).requestFocus();
        });
    }

    @Override
    public void onBackPressed() {
        if (keyboardPanel != null && keyboardPanel.getVisibility() == View.VISIBLE) {
            hideKeyboard();
        } else if (goliContainer != null && goliContainer.getVisibility() == View.VISIBLE) {
            backToForm();
        } else if (editorPanel != null && editorPanel.getVisibility() == View.VISIBLE) {
            hideEditor();
        } else if (editingCell != null) {
            exitEdit();
        } else {
            super.onBackPressed();
        }
    }

    // ---------- structure ----------

    private View header() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(6), 0, dp(6), dp(6));

        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("News Ticker", 22, TEXT, true);
        t.addView(title);
        t.addView(text("Barra notizie per Android TV", 11, LABEL, false));
        h.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        pill = new TextView(this);
        pill.setTextSize(13);
        pill.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pill.setPadding(dp(12), dp(4), dp(12), dp(4));
        h.addView(pill, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return h;
    }

    private View leftPane() {
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.MATCH_PARENT);
        lp.rightMargin = dp(14);
        left.setLayoutParams(lp);

        left.addView(sectionLabel("Impostazioni barra"));
        LinearLayout settings = card();

        LinearLayout powerRow = settingsRow("Barra notizie");
        powerSwitch = new SwitchView(this);
        setAccessory(powerRow, powerSwitch);
        powerRow.setOnClickListener(v -> onToggle());   // row click toggles: D-pad focuses rows, not the switch
        registerLeftRow(powerRow, 0);
        settings.addView(powerRow);
        settings.addView(divider());

        LinearLayout visRow = settingsRow("Visibilit\u00e0");
        visValue = text("", 14, LABEL, false);
        setAccessory(visRow, visValue);
        visRow.addView(text("  \u203A", 16, LABEL, false));
        visRow.setOnClickListener(v -> onMode());
        registerLeftRow(visRow, 1);
        settings.addView(visRow);
        settings.addView(divider());

        LinearLayout linesRow = settingsRow("Righe");
        linesValue = text("", 14, LABEL, true);
        setAccessory(linesRow, linesValue);
        linesRow.addView(text("  \u203A", 16, LABEL, false));
        linesRow.setOnClickListener(v -> {
            Prefs.setLines(this, Prefs.lines(this) == 1 ? 2 : 1);
            syncAll();
            scheduleRefresh();
        });
        registerLeftRow(linesRow, 2);
        settings.addView(linesRow);
        settings.addView(divider());

        LinearLayout styleRow = settingsRow("Stile barra");
        styleValue = text("", 14, LABEL, true);
        setAccessory(styleRow, styleValue);
        styleRow.addView(text("  \u203A", 16, LABEL, false));
        styleRow.setOnClickListener(v -> {
            Prefs.setStyleIndex(this, (Prefs.styleIndex(this) + 1) % TickerView.STYLE_NAMES.length);
            syncAll();
            scheduleRefresh();
        });
        registerLeftRow(styleRow, 3);
        settings.addView(styleRow);

        left.addView(settings, matchWrap());

        left.addView(withTopMargin(sectionLabel("Riepilogo pesi"), dp(10)));
        LinearLayout pieCard = card();
        pie = new PieChartView(this);
        pieCard.addView(pie, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(110)));
        left.addView(withTopMargin(pieCard, dp(6)));

        // flat iOS text buttons under the summary
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(flatBtn("Aggiorna ora", ACCENT, v ->
                startForegroundService(new Intent(this, TickerService.class))));
        actions.addView(flatBtn("Chiudi", ACCENT, v -> finishAndRemoveTask()));
        left.addView(withTopMargin(actions, dp(4)));
        return left;
    }

    /** Flat text button, focused like the cells; color picks the label tint. */
    private TextView flatBtn(String label, int color, View.OnClickListener onClick) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(14);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(color);
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(onClick);
        styleFlat(b, false);
        b.setOnFocusChangeListener((v, has) -> styleFlat(b, has));
        b.setLayoutParams(new LinearLayout.LayoutParams(0, dp(38), 1f));
        return b;
    }

    /** Deterministic cross-column focus: RIGHT on a settings row lands on its grid row. */
    private void registerLeftRow(LinearLayout row, int index) {
        leftRows.add(row);
        row.setOnKeyListener((v, k, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN && k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                focusGrid(index);
                return true;
            }
            return false;
        });
    }

    private void focusGrid(int leftIndex) {
        if (!cells.isEmpty()) {
            cells.get(Math.min(2 * leftIndex, cells.size() - 1)).requestFocus();
        }
    }

    /** LEFT from a topic cell jumps back to the settings column, aligned by row. */
    private void focusSettings(int gridRow) {
        if (!leftRows.isEmpty()) {
            leftRows.get(Math.min(gridRow, leftRows.size() - 1)).requestFocus();
        }
    }

    private void styleFlat(TextView b, boolean focused) {
        GradientDrawable bgd = new GradientDrawable();
        bgd.setCornerRadius(dp(10));
        if (focused) {
            bgd.setColor(CARD_HI);
            bgd.setStroke(dp(2), ACCENT);
        } else {
            bgd.setColor(Color.TRANSPARENT);
        }
        b.setBackground(bgd);
    }

    private View rightPane() {
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        right.setLayoutParams(lp);

        right.addView(sectionLabel("Argomenti e pesi"));
        topicsCard = card();
        right.addView(topicsCard, matchWrap());

        hint = text(hintIdle(), 12, LABEL, false);
        hint.setPadding(dp(6), dp(8), dp(6), 0);
        right.addView(hint, matchWrap());
        rebuildTopics();
        return right;
    }

    /** Rebuilds the topic grid from the current catalog plus the manage-categories row. */
    private void rebuildTopics() {
        editingCell = null;
        cells.clear();
        topicsCard.removeAllViews();

        for (int i = 0; i < catalog.size(); i += 2) {
            int row = i / 2;
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.addView(topicCell(catalog.get(i), row, 0));
            if (i + 1 < catalog.size()) {
                r.addView(topicCell(catalog.get(i + 1), row, 1));
            } else {
                r.addView(filler());
            }
            topicsCard.addView(r, matchWrap());
        }

        LinearLayout addRow = settingsRow("+ Modifica categorie");
        addRow.setOnClickListener(v -> showEditorList());
        topicsCard.addView(addRow);
    }

    /** One topic pill: color dot, name, weight share. Click arms the weight edit. */
    private View topicCell(Feed f, int gridRow, int col) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.HORIZONTAL);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setPadding(dp(10), 0, dp(10), 0);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setLongClickable(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1f);
        lp.setMargins(0, dp(1), 0, dp(1));
        cell.setLayoutParams(lp);

        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dot.setBackground(dotBg);
        cell.addView(dot, new LinearLayout.LayoutParams(dp(9), dp(9)));

        TextView name = text(f.name, 14, TEXT, false);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        np.leftMargin = dp(8);
        name.setLayoutParams(np);
        cell.addView(name);

        TextView pct = text("", 13, LABEL, true);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.leftMargin = dp(8);
        pct.setLayoutParams(pp);
        cell.addView(pct);

        cell.setTag(new RowRefs(f, dot, name, pct, gridRow));
        cell.setOnClickListener(v -> onCellClick(cell));
        cell.setOnFocusChangeListener((v, has) -> {
            if (!has && editingCell == cell) exitEdit();   // moving focus confirms
            styleCell(cell, has);
        });
        cell.setOnKeyListener((v, k, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (cell != editingCell && k == KeyEvent.KEYCODE_DPAD_LEFT && col == 0) {
                focusSettings(gridRow);                    // deterministic jump to settings
                return true;
            }
            return onCellKey(cell, k, e);
        });
        cells.add(cell);
        return cell;
    }

    private View filler() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        v.setVisibility(View.INVISIBLE);
        return v;
    }

    /** Settings/action row: title left, hairline separators added by the caller. */
    private LinearLayout settingsRow(String title) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setFocusable(true);
        row.setClickable(true);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        TextView t = text(title, 15, TEXT, false);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setOnFocusChangeListener((v, has) -> {
            GradientDrawable bgd = new GradientDrawable();
            bgd.setCornerRadius(dp(10));
            if (has) {
                bgd.setColor(CARD_HI);
                bgd.setStroke(dp(2), ACCENT);
                t.setTextColor(ACCENT);
            } else {
                bgd.setColor(Color.TRANSPARENT);
                t.setTextColor(TEXT);
            }
            row.setBackground(bgd);
        });
        return row;
    }

    private void setAccessory(LinearLayout row, View accessory) {
        row.addView(accessory, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // ---------- cell interactions ----------

    /**
     * Click arms the volume keys: disabled topics get enabled and enter edit right away,
     * an enabled topic enters edit, clicking the editing cell again confirms.
     */
    private void onCellClick(LinearLayout cell) {
        if (editingCell == cell) {
            exitEdit();
            return;
        }
        if (editingCell != null) exitEdit();
        RowRefs refs = (RowRefs) cell.getTag();
        List<String> sel = new ArrayList<>(Prefs.selection(this));
        if (!sel.contains(refs.feed.name)) {
            sel.add(refs.feed.name);
            Prefs.setSelection(this, sel);
        }
        syncAll();
        enterEdit(cell);
    }

    /**
     * Weight edit keys: up/down (and volume/channel, when a remote sends them) move the
     * weight; OK confirms; LEFT confirms and jumps to the settings column.
     */
    private boolean onCellKey(LinearLayout cell, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || cell != editingCell) return false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                bumpWeight(cell, +5);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                bumpWeight(cell, -5);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
                exitEdit();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                exitEdit();
                focusSettings(((RowRefs) cell.getTag()).row);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                exitEdit();
                editCategory(((RowRefs) cell.getTag()).feed);
                return true;
            default:
                return false;
        }
    }

    private void enterEdit(LinearLayout cell) {
        editingCell = cell;
        hint.setText("SU/GI\u00f9: peso \u00b15  \u2022  GI\u00f9 al minimo: spegne  \u2022  OK: conferma");
        styleCell(cell, cell.isFocused());
    }

    private void exitEdit() {
        LinearLayout cell = editingCell;
        editingCell = null;
        hint.setText(hintIdle());
        if (cell != null) styleCell(cell, cell.isFocused());
        scheduleRefresh();
    }

    /** Weight step; VOL GIÙ below the minimum switches the topic off for good. */
    private void bumpWeight(LinearLayout cell, int delta) {
        RowRefs refs = (RowRefs) cell.getTag();
        List<String> sel = new ArrayList<>(Prefs.selection(this));
        if (!sel.contains(refs.feed.name)) return;
        int w = Prefs.weight(this, refs.feed.name);
        if (delta < 0 && w <= 5) {
            sel.remove(refs.feed.name);
            Prefs.setSelection(this, sel);
            exitEdit();
            syncAll();
            return;
        }
        Prefs.setWeight(this, refs.feed.name, Math.max(5, w + delta));
        syncAll();
    }

    private void onToggle() {
        boolean v = !Prefs.enabled(this);
        Prefs.setEnabled(this, v);
        Intent i = new Intent(this, TickerService.class);
        if (v) startForegroundService(i);
        else stopService(i);
        syncAll();
    }

    private void onMode() {
        Prefs.setMode(this, "always".equals(Prefs.mode(this)) ? "home" : "always");
        syncAll();
    }

    /** Re-arms the service fetch with the new prefs, debounced while D-pad spamming. */
    private void scheduleRefresh() {
        if (!Prefs.enabled(this)) return;
        if (pendingRefresh != null) ui.removeCallbacks(pendingRefresh);
        pendingRefresh = () -> startForegroundService(new Intent(this, TickerService.class));
        ui.postDelayed(pendingRefresh, 800);
    }

    // ---------- render ----------

    private void syncAll() {
        boolean on = Prefs.enabled(this);
        pill.setText(on ? "\u25CF Attiva" : "\u25CF Spenta");
        int pc = on ? GREEN : RED;
        pill.setTextColor(pc);
        GradientDrawable pb = new GradientDrawable();
        pb.setCornerRadius(dp(12));
        pb.setColor((pc & 0x00FFFFFF) | 0x22000000);
        pill.setBackground(pb);

        powerSwitch.setOn(on);
        visValue.setText(visibleLabel());
        linesValue.setText(String.valueOf(Prefs.lines(this)));
        styleValue.setText(TickerView.STYLE_NAMES[Prefs.styleIndex(this)]);

        renderPie();
        for (LinearLayout cell : cells) styleCell(cell, cell.isFocused());
        if (editingCell != null) {
            hint.setText("SU/GI\u00f9: peso \u00b15  \u2022  GI\u00f9 al minimo: spegne  \u2022  OK: conferma  \u2022  \u2192: modifica");
        } else {
            hint.setText(hintIdle());
        }
    }

    private String visibleLabel() {
        return "always".equals(Prefs.mode(this)) ? "Sempre" : "Solo home";
    }

    private String hintIdle() {
        return "OK: peso con SU/GI\u00f9 \u00b15  \u2022  \u2192 modifica categoria  \u2022  \u2190 impostazioni";
    }

    private void renderPie() {
        List<String> sel = Prefs.selection(this);
        var shares = Prefs.shares(this);
        List<PieChartView.Slice> slices = new ArrayList<>();
        for (Feed f : catalog) {
            if (sel.contains(f.name)) {
                slices.add(new PieChartView.Slice(colorFor(f), shares.getOrDefault(f.name, 0)));
            }
        }
        pie.setSlices(slices);
        pie.setCenterText(slices.isEmpty() ? "\u2014" : slices.size() + " attivi");
    }

    /** Cell look by state: on/off sets dot, name and share; focus adds wash + amber border. */
    private void styleCell(LinearLayout cell, boolean focused) {
        RowRefs refs = (RowRefs) cell.getTag();
        Feed f = refs.feed;
        boolean on = Prefs.selection(this).contains(f.name);
        boolean editing = cell == editingCell;
        int pct = Prefs.shares(this).getOrDefault(f.name, 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        if (editing) {
            bg.setColor(0x40FFC93C);
            bg.setStroke(dp(2), ACCENT);
        } else if (focused) {
            bg.setColor(CARD_HI);
            bg.setStroke(dp(2), ACCENT);
        } else {
            bg.setColor(Color.TRANSPARENT);
        }
        cell.setBackground(bg);

        ((GradientDrawable) refs.dot.getBackground()).setColor(
                on ? colorFor(f) : (colorFor(f) & 0x00FFFFFF) | 0x40000000);
        refs.name.setTextColor(on ? (focused || editing ? ACCENT : TEXT) : LABEL);
        if (editing) {
            refs.pct.setText("\u25B2 " + pct + "% \u25BC");   // up/down adjust the weight
        } else {
            refs.pct.setText(on ? pct + "%" : "\u2014");
        }
        refs.pct.setTextColor(on ? (editing ? ACCENT : (focused ? TEXT : LABEL)) : 0x50FFFFFF);
    }

    // ---------- small builders ----------

    private View sectionLabel(String s) {
        TextView t = text(s.toUpperCase(), 12, LABEL, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(6), 0, 0, dp(6));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bgd = new GradientDrawable();
        bgd.setCornerRadius(dp(12));
        bgd.setColor(CARD);
        c.setBackground(bgd);
        c.setPadding(dp(4), dp(4), dp(4), dp(4));
        return c;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(SEP);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.setMargins(dp(12), 0, dp(12), 0);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView text(String s, float sp, int color, boolean medium) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (medium) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private View withTopMargin(View v, int m) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        LinearLayout.LayoutParams lp = p instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) p : matchWrap();
        lp.topMargin = m;
        v.setLayoutParams(lp);
        return v;
    }

    /** Stable color per feed, by catalog position (same colors drive the pie and the dots). */
    private int colorFor(Feed f) {
        for (int i = 0; i < catalog.size(); i++) {
            if (catalog.get(i).name.equals(f.name)) return PALETTE[i % PALETTE.length];
        }
        return PALETTE[0];
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    // ---------- category editor ----------

    /** Full-screen editor, two views inside one scroll: the category list and the form. */
    private View buildEditorPanel() {
        editorPanel = new LinearLayout(this);
        editorPanel.setOrientation(LinearLayout.VERTICAL);
        editorPanel.setBackgroundColor(BG);
        editorPanel.setPadding(dp(20), dp(10), dp(20), dp(16));
        editorPanel.setVisibility(View.GONE);

        editorTitle = text("Categorie", 20, TEXT, true);
        editorTitle.setPadding(dp(4), 0, 0, dp(6));
        editorPanel.addView(editorTitle);

        ScrollView esc = new ScrollView(this);
        esc.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(listContainer, matchWrap());

        formContainer = new LinearLayout(this);
        formContainer.setOrientation(LinearLayout.VERTICAL);
        formContainer.setVisibility(View.GONE);

        LinearLayout form = card();
        tipoRow = settingsRow("Tipo");
        tipoVal = text("", 14, LABEL, true);
        setAccessory(tipoRow, tipoVal);
        tipoRow.addView(text("  \u203A", 16, LABEL, false));
        tipoRow.setOnClickListener(v -> cycleType());
        form.addView(tipoRow);
        form.addView(divider());

        nomeRow = settingsRow("Nome");
        nomeVal = text("", 14, LABEL, true);
        setAccessory(nomeRow, nomeVal);
        nomeRow.addView(text("  \u203A", 16, LABEL, false));
        nomeRow.setOnClickListener(v -> showKeyboard(nameBuf, nomeVal, "NOME", nomeRow, null));
        form.addView(nomeRow);
        form.addView(divider());

        queryRow = settingsRow(QUERY_LABELS[0]);
        queryVal = text("", 14, LABEL, true);
        setAccessory(queryRow, queryVal);
        queryRow.addView(text("  \u203A", 16, LABEL, false));
        queryRow.setOnClickListener(v -> {
            if (TYPE_KEYS[editType].equals(Topic.ANSA)) cycleSlug();
            else if (TYPE_KEYS[editType].equals(Topic.GOLIARDIA)) showGoliardia();
            else showKeyboard(queryBuf, queryVal, QUERY_LABELS[editType], queryRow, null);
        });
        form.addView(queryRow);
        formContainer.addView(withTopMargin(form, dp(8)));

        // directory of main Italian RSS feeds, shown only when the type is RSS
        rssBox = new LinearLayout(this);
        rssBox.setOrientation(LinearLayout.VERTICAL);
        rssBox.setVisibility(View.GONE);
        formContainer.addView(rssBox, matchWrap());

        content.addView(withTopMargin(formContainer, dp(8)), matchWrap());

        editorNote = text(" ", 12, RED, false);
        editorNote.setPadding(dp(6), dp(6), 0, 0);
        content.addView(editorNote, matchWrap());

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        deleteBtn = flatBtn("Elimina", RED, v -> deleteTopic());
        btns.addView(deleteBtn, new LinearLayout.LayoutParams(0, dp(38), 1f));
        btns.addView(flatBtn("Salva", ACCENT, v -> saveTopic()), new LinearLayout.LayoutParams(0, dp(38), 1f));
        btns.addView(flatBtn("Annulla", LABEL, v -> showEditorList()), new LinearLayout.LayoutParams(0, dp(38), 1f));
        content.addView(withTopMargin(btns, dp(8)));

        content.addView(withTopMargin(text(
                "Google News: qualsiasi termine (INTER, NBA...). Sezione ANSA: scorre con OK. RSS: indirizzo del feed. Annulla torna all'elenco.",
                11, LABEL, false), dp(8)));

        esc.addView(content, matchWrap());
        editorPanel.addView(esc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        goliContainer = new LinearLayout(this);
        goliContainer.setOrientation(LinearLayout.VERTICAL);
        goliContainer.setVisibility(View.GONE);
        content.addView(goliContainer, matchWrap());
        return editorPanel;
    }

    /** Mentre un overlay è aperto il focus non deve poter scappare nella pagina sottostante. */
    private void setMainPageFocusable(boolean on) {
        if (mainScroll != null) {
            mainScroll.setDescendantFocusability(on
                    ? ViewGroup.FOCUS_BEFORE_DESCENDANTS
                    : ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
    }

    private void setEditorFocusable(boolean on) {
        if (editorPanel != null) {
            editorPanel.setDescendantFocusability(on
                    ? ViewGroup.FOCUS_BEFORE_DESCENDANTS
                    : ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
    }

    /** List mode: every category, click opens the form. */
    private void showEditorList() {
        setMainPageFocusable(false);
        listContainer.setVisibility(View.VISIBLE);
        formContainer.setVisibility(View.GONE);
        goliContainer.setVisibility(View.GONE);
        editorTitle.setText("Categorie");
        listRows.clear();
        listContainer.removeAllViews();

        LinearLayout addRow = settingsRow("+ Nuova categoria");
        ((TextView) addRow.getChildAt(0)).setTextColor(ACCENT);
        addRow.setOnClickListener(v -> showEditorForm(null));
        listRows.add(addRow);
        listContainer.addView(addRow, matchWrap());
        listContainer.addView(divider());

        for (Topic t : Prefs.allTopics(this)) {
            LinearLayout row = settingsRow(t.name);
            String src = Topic.ANSA.equals(t.type) ? "ANSA" : Topic.RSS.equals(t.type) ? "RSS"
                    : Topic.GOLIARDIA.equals(t.type) ? "Goliardia" : "Google";
            setAccessory(row, text(src, 12, LABEL, false));
            row.setOnClickListener(v -> showEditorForm(t));
            listRows.add(row);
            listContainer.addView(row, matchWrap());
            listContainer.addView(divider());
        }

        listContainer.setVisibility(View.VISIBLE);
        formContainer.setVisibility(View.GONE);
        editorPanel.setVisibility(View.VISIBLE);
        editorNote.setText(" ");
        editorPanel.post(() -> { if (!listRows.isEmpty()) listRows.get(0).requestFocus(); });
    }

    /** Form mode for an existing topic or a new one (null). */
    private void showEditorForm(Topic t) {
        setMainPageFocusable(false);
        editingTopic = t;
        nameBuf.setLength(0);
        queryBuf.setLength(0);
        goliItems.clear();
        keyOnClose = null;
        if (t == null) {
            editType = 0;
            slugIdx = 1;
        } else {
            nameBuf.append(t.name);
            queryBuf.append(t.q);
            editType = Topic.ANSA.equals(t.type) ? 1 : Topic.RSS.equals(t.type) ? 2
                    : Topic.GOLIARDIA.equals(t.type) ? 3 : 0;
            slugIdx = 1;
            for (int i = 0; i < SLUGS.length; i++) if (SLUGS[i].equals(t.q)) slugIdx = i;
            goliItems.addAll(t.items);
        }
        editorTitle.setText(t == null ? "Nuova categoria" : "Modifica: " + t.name);
        deleteBtn.setVisibility(t == null ? View.GONE : View.VISIBLE);
        listContainer.setVisibility(View.GONE);
        formContainer.setVisibility(View.VISIBLE);
        editorNote.setText(" ");
        refreshEditorValues();
        editorPanel.setVisibility(View.VISIBLE);
        editorPanel.post(() -> tipoRow.requestFocus());
    }

    private void hideEditor() {
        editorPanel.setVisibility(View.GONE);
        setMainPageFocusable(true);
        if (!leftRows.isEmpty()) leftRows.get(0).requestFocus();
    }

    /** Opens the form straight on an existing category (D-pad RIGHT from its cell). */
    private void editCategory(Feed f) {
        Topic t = null;
        for (Topic x : Prefs.allTopics(this)) if (x.name.equals(f.name)) { t = x; break; }
        showEditorForm(t != null ? t : new Topic(f.name, Topic.GNEWS, ""));
    }

    // ---------- goliardia item list ----------

    private void showGoliardia() {
        setMainPageFocusable(false);
        listContainer.setVisibility(View.GONE);
        formContainer.setVisibility(View.GONE);
        goliContainer.setVisibility(View.VISIBLE);
        editorTitle.setText("Goliardia \u2014 " + (nameBuf.length() == 0 ? "notizie" : nameBuf));
        refreshGoliList(0);
    }

    /**
     * Item rows are built as two SIBLING focusables — [testo][✕] — so the D-pad can
     * actually reach the delete button (a ✕ inside the row bounds would never get focus).
     * focusRow: which row to focus after the rebuild (-1 = leave focus alone).
     */
    private void refreshGoliList(int focusRow) {
        goliContainer.removeAllViews();
        goliRows.clear();

        LinearLayout add = settingsRow("+ Aggiungi notizia");
        ((TextView) add.getChildAt(0)).setTextColor(ACCENT);
        add.setOnClickListener(v -> {
            goliItems.add("");
            int idx = goliItems.size() - 1;
            refreshGoliList(idx + 1);
            editGoliItem(idx);
        });
        goliContainer.addView(add, matchWrap());
        goliContainer.addView(divider());
        goliRows.add(add);

        for (int i = 0; i < goliItems.size(); i++) {
            final int idx = i;
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout main = new LinearLayout(this);
            main.setOrientation(LinearLayout.HORIZONTAL);
            main.setGravity(Gravity.CENTER_VERTICAL);
            main.setPadding(dp(12), 0, dp(8), 0);
            main.setFocusable(true);
            main.setClickable(true);
            main.setLayoutParams(new LinearLayout.LayoutParams(0, dp(38), 1f));
            boolean empty = goliItems.get(i).trim().isEmpty();
            TextView t = text(empty ? "(vuota \u2014 premi OK per scriverla)" : goliItems.get(i),
                    14, empty ? LABEL : TEXT, false);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            main.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            styleGoliMain(main, false);
            main.setOnFocusChangeListener((v, has) -> styleGoliMain(main, has));
            main.setOnClickListener(v -> editGoliItem(idx));
            line.addView(main);

            TextView del = text("\u2715", 15, RED, true);
            del.setGravity(Gravity.CENTER);
            del.setFocusable(true);
            del.setClickable(true);
            del.setLayoutParams(new LinearLayout.LayoutParams(dp(52), dp(38)));
            styleGoliDel(del, false);
            del.setOnFocusChangeListener((v, has) -> styleGoliDel(del, has));
            del.setOnClickListener(v -> {
                goliItems.remove(idx);
                refreshGoliList(idx);
                refreshEditorValues();
            });
            line.addView(del);

            goliContainer.addView(line, matchWrap());
            goliContainer.addView(divider());
            goliRows.add(main);
        }

        LinearLayout done = settingsRow("\u2190 Fatto");
        done.setOnClickListener(v -> backToForm());
        goliContainer.addView(withTopMargin(done, dp(6)), matchWrap());
        goliRows.add(done);

        if (focusRow >= 0 && keyboardPanel.getVisibility() != View.VISIBLE) {
            editorPanel.post(() -> {
                if (goliRows.isEmpty()) return;
                goliRows.get(Math.min(focusRow, goliRows.size() - 1)).requestFocus();
            });
        }
    }

    private void styleGoliMain(LinearLayout main, boolean focused) {
        GradientDrawable bgd = new GradientDrawable();
        bgd.setCornerRadius(dp(10));
        if (focused) {
            bgd.setColor(CARD_HI);
            bgd.setStroke(dp(2), ACCENT);
        } else {
            bgd.setColor(Color.TRANSPARENT);
        }
        main.setBackground(bgd);
    }

    private void styleGoliDel(TextView del, boolean focused) {
        GradientDrawable bgd = new GradientDrawable();
        bgd.setCornerRadius(dp(8));
        if (focused) {
            bgd.setColor(CARD_HI);
            bgd.setStroke(dp(2), RED);
        } else {
            bgd.setColor(Color.TRANSPARENT);
        }
        del.setBackground(bgd);
    }

    /** Opens the keyboard on one joke; closing writes the text back and refreshes the list. */
    private void editGoliItem(int idx) {
        if (idx < 0 || idx >= goliItems.size()) return;
        goliEditIdx = idx;
        StringBuilder buf = new StringBuilder(goliItems.get(idx));
        showKeyboard(buf, null, "NOTIZIA " + (idx + 1), null, () -> {
            String s = buf.toString().trim();
            if (idx < goliItems.size()) goliItems.set(idx, s);
            refreshGoliList(idx + 1);
            refreshEditorValues();
        });
    }

    private void backToForm() {
        goliContainer.setVisibility(View.GONE);
        formContainer.setVisibility(View.VISIBLE);
        refreshEditorValues();
        editorPanel.post(() -> queryRow.requestFocus());
    }

    private void refreshEditorValues() {
        tipoVal.setText(TYPE_LABELS[editType]);
        boolean goli = TYPE_KEYS[editType].equals(Topic.GOLIARDIA);
        nomeVal.setText(nameBuf.length() == 0 ? "\u2014" : nameBuf.toString());
        if (goli) {
            queryVal.setText(goliItems.size() + " notizie");
        } else {
            queryVal.setText(queryBuf.length() == 0 ? "\u2014" : queryBuf.toString());
        }
        ((TextView) queryRow.getChildAt(0)).setText(QUERY_LABELS[editType]);
        boolean rss = TYPE_KEYS[editType].equals(Topic.RSS);
        rssBox.setVisibility(rss ? View.VISIBLE : View.GONE);
        if (rss) refreshRssList();
    }

    /** The curated Italian feeds: click compiles URL (and name, if empty). */
    private void refreshRssList() {
        rssBox.removeAllViews();
        List<String[]> dir = rssDir();
        TextView head = text("RSS ITALIANI  \u2022  " + dir.size() + " fonti (elenco aggiornato all'apertura dell'app)",
                12, LABEL, true);
        head.setPadding(dp(6), dp(8), 0, dp(4));
        rssBox.addView(head, matchWrap());

        for (String[] p : dir) {
            LinearLayout row = settingsRow(p[0]);
            setAccessory(row, text("usa \u203A", 12, ACCENT, false));
            row.setOnClickListener(v -> {
                queryBuf.setLength(0);
                queryBuf.append(p[1]);
                if (nameBuf.length() == 0) nameBuf.append(p[0]);
                refreshEditorValues();
                editorNote.setText("URL inserito: regola il nome se vuoi, poi Salva.");
            });
            rssBox.addView(row, matchWrap());
            rssBox.addView(divider());
        }
    }

    private List<String[]> rssDir() {
        List<String[]> cached = Prefs.rssDirectory(this);
        if (!cached.isEmpty()) return cached;
        List<String[]> out = new ArrayList<>();
        for (String[] p : RssDirectory.BUILT_IN) out.add(p);
        return out;
    }

    /** Silent background refresh of the directory; failure keeps the cached list. */
    private void refreshRssDirectory() {
        if (RssDirectory.DIR_URL.isEmpty()) return;
        new Thread(() -> {
            try {
                List<String[]> pairs = RssDirectory.fetchRemote();
                if (pairs.isEmpty()) return;
                Prefs.setRssDirectory(this, pairs);
                ui.post(() -> {
                    if (editorPanel != null && editorPanel.getVisibility() == View.VISIBLE
                            && TYPE_KEYS[editType].equals(Topic.RSS)) {
                        refreshRssList();
                    }
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private void cycleType() {
        editType = (editType + 1) % TYPE_KEYS.length;
        if (TYPE_KEYS[editType].equals(Topic.ANSA) && queryBuf.length() == 0) {
            slugIdx = 1;
            queryBuf.append(SLUGS[slugIdx]);
        }
        if (TYPE_KEYS[editType].equals(Topic.GOLIARDIA) && goliItems.isEmpty()) {
            for (String s : GOLI_SAMPLES) goliItems.add(s);   // start with a few jokes
        }
        editorNote.setText(" ");
        refreshEditorValues();
    }

    private void cycleSlug() {
        slugIdx = (slugIdx + 1) % SLUGS.length;
        queryBuf.setLength(0);
        queryBuf.append(SLUGS[slugIdx]);
        refreshEditorValues();
    }

    private void saveTopic() {
        String name = nameBuf.toString().trim();
        String type = TYPE_KEYS[editType];
        String q = queryBuf.toString().trim();
        if (name.isEmpty()) {
            editorNote.setText("Dai un nome all'argomento (riga Nome).");
            return;
        }
        List<String> items = null;
        if (Topic.GOLIARDIA.equals(type)) {
            items = new ArrayList<>();
            for (String s : goliItems) if (!s.trim().isEmpty()) items.add(s.trim());
        } else {
            if (Topic.RSS.equals(type) && !q.startsWith("http")) {
                editorNote.setText("L'URL del feed deve iniziare con http.");
                return;
            }
            if (!Topic.ANSA.equals(type) && q.isEmpty()) {
                editorNote.setText(QUERY_LABELS[editType] + " mancante.");
                return;
            }
        }
        List<Topic> ts = new ArrayList<>(Prefs.allTopics(this));
        // rename: carry selection and weight over to the new name
        String oldName = editingTopic == null ? null : editingTopic.name;
        if (oldName != null && !oldName.equals(name)) {
            int w = Prefs.weight(this, oldName);
            Prefs.setWeight(this, name, w);
            List<String> sel = new ArrayList<>(Prefs.selection(this));
            if (sel.contains(oldName)) {
                sel.remove(oldName);
                if (!sel.contains(name)) sel.add(name);
                Prefs.setSelection(this, sel);
            }
        }
        if (oldName != null) ts.removeIf(t -> t.name.equals(oldName));
        ts.removeIf(t -> t.name.equals(name));
        ts.add(new Topic(name, type, q, items));
        Prefs.setAllTopics(this, ts);

        List<String> sel2 = new ArrayList<>(Prefs.selection(this));
        if (!sel2.contains(name)) sel2.add(name);
        Prefs.setSelection(this, sel2);

        showEditorList();
        catalog = Prefs.allFeeds(this);
        rebuildTopics();
        syncAll();
        scheduleRefresh();
    }

    private void deleteTopic() {
        List<Topic> ts = new ArrayList<>(Prefs.allTopics(this));
        if (editingTopic == null) return;
        if (ts.size() <= 1) {
            editorNote.setText("Non puoi eliminare l'ultima categoria.");
            return;
        }
        ts.removeIf(t -> t.name.equals(editingTopic.name));
        Prefs.setAllTopics(this, ts);
        List<String> sel = new ArrayList<>(Prefs.selection(this));
        sel.remove(editingTopic.name);
        Prefs.setSelection(this, sel);

        showEditorList();
        catalog = Prefs.allFeeds(this);
        rebuildTopics();
        syncAll();
        scheduleRefresh();
    }

    // ---------- on-screen keyboard ----------

    private View buildKeyboardPanel() {
        keyboardPanel = new LinearLayout(this);
        keyboardPanel.setOrientation(LinearLayout.VERTICAL);
        keyboardPanel.setBackgroundColor(0xFF000000);
        keyboardPanel.setGravity(Gravity.CENTER);
        keyboardPanel.setVisibility(View.GONE);
        keyboardPanel.setPadding(dp(24), dp(16), dp(24), dp(16));
        keyboardPanel.setOnKeyListener((v, k, e) ->
                e.getAction() == KeyEvent.ACTION_DOWN && k == KeyEvent.KEYCODE_BACK && hideKeyboard());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bgd = new GradientDrawable();
        bgd.setCornerRadius(dp(14));
        bgd.setColor(0xFF0A0A0C);
        bgd.setStroke(dp(1), 0xFF2E2E34);
        box.setBackground(bgd);
        box.setPadding(dp(18), dp(12), dp(18), dp(14));

        keyTitle = text("Nome", 12, LABEL, true);
        keyTitle.setLetterSpacing(0.08f);
        box.addView(keyTitle);
        keyPreview = text("", 17, TEXT, true);
        GradientDrawable pv = new GradientDrawable();
        pv.setCornerRadius(dp(8));
        pv.setColor(0xFF101013);
        pv.setStroke(dp(1), 0xFF2E2E34);
        keyPreview.setBackground(pv);
        keyPreview.setPadding(dp(10), dp(8), dp(10), dp(8));
        keyPreview.setMinHeight(dp(42));
        box.addView(withTopMargin(keyPreview, dp(6)));

        keysBox = new LinearLayout(this);
        keysBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(withTopMargin(keysBox, dp(8)));

        box.addView(withTopMargin(text("Back o ⏎ chiudono la tastiera", 11, LABEL, false), dp(6)));

        keyboardPanel.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return keyboardPanel;
    }

    /** Rebuilds the key grid for the current page (lettere o &123). */
    private void buildKeys() {
        letterKeys.clear();
        keyKinds.clear();
        keysBox.removeAllViews();
        firstKey = null;
        LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        kp.setMargins(dp(3), dp(4), dp(3), dp(4));

        if (numPage) {
            LinearLayout r1 = new LinearLayout(this);
            for (String s : new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"}) {
                r1.addView(makeKey(s, 1, KeyKind.CHAR, null), copyLp(kp));
            }
            keysBox.addView(withTopMargin(r1, dp(2)), matchWrap());

            LinearLayout r2 = new LinearLayout(this);
            for (String s : new String[]{"@", "#", "€", "%", "&", "*", "(", ")", "+", "="}) {
                r2.addView(makeKey(s, 1, KeyKind.CHAR, null), copyLp(kp));
            }
            keysBox.addView(withTopMargin(r2, dp(2)), matchWrap());

            LinearLayout r3 = new LinearLayout(this);
            for (String s : new String[]{"/", ":", ";", "?", "!", "\"", "'", "_", "-"}) {
                r3.addView(makeKey(s, 1, KeyKind.CHAR, null), copyLp(kp));
            }
            keysBox.addView(withTopMargin(r3, dp(2)), matchWrap());

            LinearLayout r4 = new LinearLayout(this);
            r4.addView(makeKey("ABC", 2, KeyKind.PAGE, this::togglePage), copyLp(kp));
            r4.addView(makeKey("_", 1, KeyKind.CHAR, null), copyLp(kp));
            r4.addView(makeKey("", 6, KeyKind.SPACE, this::typeSpace), copyLp(kp));
            r4.addView(makeKey(",", 1, KeyKind.CHAR, null), copyLp(kp));
            r4.addView(makeKey("⌫", 2, KeyKind.FUNC, this::delChar), copyLp(kp));
            keysBox.addView(withTopMargin(r4, dp(2)), matchWrap());
        } else {
            LinearLayout r1 = new LinearLayout(this);
            for (String s : new String[]{"q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "è"}) {
                r1.addView(makeKey(s, 1, KeyKind.LETTER, null), copyLp(kp));
            }
            r1.addView(makeKey("⌫", 1.4f, KeyKind.FUNC, this::delChar), copyLp(kp));
            keysBox.addView(withTopMargin(r1, dp(2)), matchWrap());

            LinearLayout r2 = new LinearLayout(this);
            for (String s : new String[]{"a", "s", "d", "f", "g", "h", "j", "k", "l", "ò", "à"}) {
                r2.addView(makeKey(s, 1, KeyKind.LETTER, null), copyLp(kp));
            }
            r2.addView(makeKey("⏎", 1.4f, KeyKind.RETURN, this::hideKeyboard), copyLp(kp));
            keysBox.addView(withTopMargin(r2, dp(2)), matchWrap());

            LinearLayout r3 = new LinearLayout(this);
            shiftKey = makeKey("↑", 1.4f, KeyKind.SHIFT, this::tapShift);
            r3.addView(shiftKey, copyLp(kp));
            for (String s : new String[]{"z", "x", "c", "v", "b", "n", "m", "ù", ",", ".", "'"}) {
                r3.addView(makeKey(s, 1, KeyKind.LETTER, null), copyLp(kp));
            }
            keysBox.addView(withTopMargin(r3, dp(2)), matchWrap());

            LinearLayout r4 = new LinearLayout(this);
            r4.addView(makeKey("&123", 2, KeyKind.PAGE, this::togglePage), copyLp(kp));
            r4.addView(makeKey("", 7, KeyKind.SPACE, this::typeSpace), copyLp(kp));
            r4.addView(makeKey("OK", 2, KeyKind.OK, this::hideKeyboard), copyLp(kp));
            keysBox.addView(withTopMargin(r4, dp(2)), matchWrap());
        }
        relabelLetters();
    }

    private void relabelLetters() {
        boolean up = shiftOn || capsLock;
        for (TextView k : letterKeys) {
            k.setText(up ? ((String) k.getTag()).toUpperCase(Locale.ROOT)
                    : (String) k.getTag());
        }
        if (shiftKey != null) styleKey(shiftKey, shiftKey.isFocused());
    }

    /** ↑: tap = maiuscola a uno colpo, secondo tap = blocco maiuscole, terzo = rilascia. */
    private void tapShift() {
        if (!shiftOn && !capsLock) shiftOn = true;
        else if (shiftOn && !capsLock) capsLock = true;
        else {
            capsLock = false;
            shiftOn = false;
        }
        relabelLetters();
    }

    private void togglePage() {
        numPage = !numPage;
        buildKeys();
        keysBox.post(() -> { if (firstKey != null) firstKey.requestFocus(); });
    }

    private void typeSpace() {
        typeChar(" ", false);
    }

    private void delChar() {
        typeChar("DEL", false);
    }

    private static LinearLayout.LayoutParams copyLp(LinearLayout.LayoutParams src) {
        return new LinearLayout.LayoutParams(src.width, src.height, src.weight);
    }

    private TextView makeKey(String base, float weight, KeyKind kind, Runnable action) {
        TextView k = new TextView(this);
        k.setTag(base);
        keyKinds.put(k, kind);
        k.setText(base);
        k.setGravity(Gravity.CENTER);
        k.setTextSize(base.isEmpty() ? 13 : base.length() > 1 ? 13 : 18);
        k.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        k.setFocusable(true);
        k.setClickable(true);
        styleKey(k, false);
        k.setOnFocusChangeListener((v, has) -> styleKey(k, has));
        k.setOnClickListener(v -> {
            if (action != null) { action.run(); return; }
            switch (kind) {
                case LETTER:
                    typeLetter(k);
                    break;
                case CHAR:
                    typeChar(base, false);
                    break;
                default:
                    break;
            }
        });
        if (firstKey == null) firstKey = k;
        return k;
    }

    private void typeLetter(TextView k) {
        String base = (String) k.getTag();
        typeChar(shiftOn || capsLock ? base.toUpperCase(Locale.ROOT) : base, false);
        if (shiftOn && !capsLock) {          // one-shot shift used up
            shiftOn = false;
            relabelLetters();
        }
    }

    private void styleKey(TextView k, boolean focused) {
        KeyKind kind = keyKinds.get(k);
        if (kind == null) kind = KeyKind.CHAR;
        GradientDrawable bgd = new GradientDrawable();
        bgd.setCornerRadius(dp(8));
        int textColor = TEXT;
        if (kind == KeyKind.OK) {
            bgd.setColor(ACCENT);
            textColor = 0xFF151515;
            if (focused) bgd.setStroke(dp(2), 0xFFFFFFFF);
        } else if (focused) {
            bgd.setColor(0xFFFFFFFF);
            textColor = 0xFF000000;
        } else if (kind == KeyKind.FUNC || kind == KeyKind.SHIFT || kind == KeyKind.PAGE
                || kind == KeyKind.RETURN || kind == KeyKind.SPACE) {
            bgd.setColor(0xFF17171B);
            bgd.setStroke(dp(1), 0xFF3A3A40);
        } else {
            bgd.setColor(0xFF141418);
            bgd.setStroke(dp(1), 0xFF2E2E34);
        }
        if (kind == KeyKind.SHIFT && k == shiftKey && (shiftOn || capsLock) && !focused) {
            bgd.setStroke(dp(2), ACCENT);       // shift attivo
            if (capsLock) bgd.setColor(0xFFFFFFFF);
        }
        k.setBackground(bgd);
        k.setTextColor(textColor);
    }

    /** Keyboard bound to an arbitrary buffer; onClose runs when it closes (item write-back). */
    private void showKeyboard(StringBuilder buf, TextView previewTarget, String title,
                              View returnRow, Runnable onClose) {
        setEditorFocusable(false);   // le frecce non devono scappare nell'editor dietro
        keyBuf = buf;
        keyPreviewTarget = previewTarget;
        keyReturnRow = returnRow;
        keyOnClose = onClose;
        keyTitle.setText(title);
        keyPreview.setText(buf);
        shiftOn = false;
        capsLock = false;
        numPage = false;
        buildKeys();
        keyboardPanel.setVisibility(View.VISIBLE);
        if (firstKey != null) firstKey.requestFocus();
    }

    private boolean hideKeyboard() {
        keyboardPanel.setVisibility(View.GONE);
        setEditorFocusable(true);
        if (keyOnClose != null) {
            Runnable r = keyOnClose;
            keyOnClose = null;
            r.run();
        }
        if (keyReturnRow != null) keyReturnRow.requestFocus();
        return true;
    }

    private void typeChar(String ch, boolean respectCase) {
        if ("DEL".equals(ch)) {
            if (keyBuf.length() > 0) keyBuf.deleteCharAt(keyBuf.length() - 1);
        } else if (" ".equals(ch)) {
            if (keyBuf.length() == 0 || keyBuf.charAt(keyBuf.length() - 1) != ' ') keyBuf.append(' ');
        } else {
            keyBuf.append(respectCase && !caps ? ch.toLowerCase(Locale.ROOT) : ch);
        }
        keyPreview.setText(keyBuf);
        if (keyPreviewTarget != null) keyPreviewTarget.setText(
                keyBuf.length() == 0 ? "\u2014" : keyBuf.toString());
    }

    /** One cell's state: feed plus the views restyled on focus/prefs changes. */
    private static final class RowRefs {
        final Feed feed;
        final View dot;
        final TextView name;
        final TextView pct;
        final int row;

        RowRefs(Feed feed, View dot, TextView name, TextView pct, int row) {
            this.feed = feed;
            this.dot = dot;
            this.name = name;
            this.pct = pct;
            this.row = row;
        }
    }
}
