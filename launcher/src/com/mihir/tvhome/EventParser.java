package com.mihir.tvhome;

import java.text.Normalizer;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a short spoken sentence in Brazilian Portuguese into a calendar entry, e.g.
 * "dentista amanhã às 10", "reunião da escola sexta às 18h30", "aniversário da avó dia 5 de novembro".
 * Understands hoje/amanhã/depois de amanhã, weekdays, "dia N (de mês)", "N/M", and times like
 * 10, 10h, 10h30, 10:30, 10 e meia, meio-dia, "da tarde/noite". No time means an all-day event.
 */
final class EventParser {

    static final class Result {
        String title;
        Calendar day;       // midnight of the event's day
        int hour = -1, minute;
        boolean allDay() { return hour < 0; }
        boolean dateGuessed; // no date was said: it is today
    }

    private static final String[] WEEKDAYS = {"domingo", "segunda", "terca", "quarta", "quinta", "sexta", "sabado"};
    private static final String[] MONTHS = {"janeiro", "fevereiro", "marco", "abril", "maio", "junho", "julho",
            "agosto", "setembro", "outubro", "novembro", "dezembro"};
    private static final String MONTH_RE = "(janeiro|fevereiro|marco|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)";

    static Result parse(String spoken, Calendar now) {
        Result r = new Result();
        // Work on an accent-free lower-case copy, but cut the title from the original words.
        String orig = spoken.trim().replaceAll("\\s+", " ");
        StringBuilder s = new StringBuilder(fold(orig));
        Calendar today = (Calendar) now.clone();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        // ---------- date
        Calendar day = null;
        Matcher m;
        if ((m = find(s, "\\bdepois de amanha\\b")) != null) { day = plus(today, 2); blank(s, m); }
        else if ((m = find(s, "\\bamanha\\b")) != null) { day = plus(today, 1); blank(s, m); }
        else if ((m = find(s, "\\bhoje\\b")) != null) { day = (Calendar) today.clone(); blank(s, m); }
        if (day == null && (m = find(s, "\\b(?:no |na |dia )?(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b")) != null) {
            day = date(today, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) - 1, m.group(3));
            blank(s, m);
        }
        if (day == null && (m = find(s, "\\b(?:no )?(?:dia )?(\\d{1,2}) de " + MONTH_RE + "(?: de (\\d{4}))?\\b")) != null) {
            day = date(today, Integer.parseInt(m.group(1)), indexOf(MONTHS, m.group(2)), m.group(3));
            blank(s, m);
        }
        if (day == null && (m = find(s, "\\b(?:no )?dia (\\d{1,2})\\b")) != null) {
            int d = Integer.parseInt(m.group(1));
            day = (Calendar) today.clone();
            if (d < today.get(Calendar.DAY_OF_MONTH)) day.add(Calendar.MONTH, 1);
            day.set(Calendar.DAY_OF_MONTH, Math.min(d, day.getActualMaximum(Calendar.DAY_OF_MONTH)));
            blank(s, m);
        }
        if (day == null && (m = find(s, "\\b(?:(?:n|d)?(?:a|o) )?(?:proxim[ao] )?(domingo|segunda|terca|quarta|quinta|sexta|sabado)(?:-feira| feira)?(?: que vem)?\\b")) != null) {
            int target = indexOf(WEEKDAYS, m.group(1)) + 1; // Calendar.SUNDAY == 1
            int ahead = (target - today.get(Calendar.DAY_OF_WEEK) + 7) % 7;
            // "próxima terça" is the next Tuesday; "terça que vem" is the one in the following week.
            if (m.group().contains("que vem")) ahead += 7;
            else if (m.group().contains("proxim") && ahead == 0) ahead = 7;
            day = plus(today, ahead);
            blank(s, m);
        }
        if (day == null) { day = (Calendar) today.clone(); r.dateGuessed = true; }
        r.day = day;

        // ---------- time
        if ((m = find(s, "\\b(?:ao |a )?meio[- ]dia(?: e meia)?\\b")) != null) {
            r.hour = 12; r.minute = m.group().contains("e meia") ? 30 : 0;
            blank(s, m);
        } else if ((m = find(s, "\\b(?:a )?meia[- ]noite\\b")) != null) {
            r.hour = 0; blank(s, m);
        } else if ((m = find(s, "\\b(?:as |a |ao |pelas |por volta das )?(\\d{1,2})(?:(?::|h|horas?\\s?)(\\d{2})?| e (meia|\\d{1,2}))?(?:\\s?(?:h|horas?))?(?:\\s(da manha|da tarde|da noite|de manha|de tarde|de noite))?\\b")) != null
                && looksLikeTime(m)) {
            int h = Integer.parseInt(m.group(1));
            int min = m.group(2) != null ? Integer.parseInt(m.group(2))
                    : "meia".equals(m.group(3)) ? 30 : m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
            String part = m.group(4);
            if (part != null && (part.endsWith("tarde") || part.endsWith("noite")) && h < 12) h += 12;
            if (h <= 23 && min <= 59) { r.hour = h; r.minute = min; blank(s, m); }
        }

        // ---------- title: what is left, minus filler words
        String rest = s.toString().replaceAll("\\s+", " ").trim();
        String[] filler = {"marcar", "marca", "agendar", "agenda", "adicionar", "adiciona", "criar", "cria",
                "novo compromisso", "compromisso", "evento", "lembrete", "lembrar de", "lembrar", "anotar"};
        for (String f : filler) rest = rest.replaceAll("^" + f + "\\b\\s*", "").trim();
        rest = rest.replaceAll("^(o|a|um|uma|de|do|da|para|pra|no|na|as|para o|para a)\\s+", "")
                .replaceAll("\\s+(no|na|as|a|de|do|da|em|para|pra|e)$", "").trim();
        r.title = titleFrom(orig, rest);
        return r;
    }

    /** A bare number counts as a time only with "às/a/ao", "h" or "horas", minutes, or "da tarde". */
    private static boolean looksLikeTime(Matcher m) {
        String g = m.group();
        return g.matches("(?s).*\\b(as|a|ao|pelas|das)\\b.*") || g.contains("h") || g.contains(":")
                || m.group(2) != null || m.group(3) != null || m.group(4) != null;
    }

    /** Picks the words of the original sentence that survived, keeping their accents and case. */
    private static String titleFrom(String orig, String foldedRest) {
        if (foldedRest.isEmpty()) return "Compromisso";
        String[] o = orig.split(" ");
        StringBuilder out = new StringBuilder();
        int from = 0;
        for (String w : foldedRest.split(" ")) {
            for (int i = from; i < o.length; i++) {
                if (fold(o[i]).replaceAll("[^a-z0-9/:-]", "").equals(w.replaceAll("[^a-z0-9/:-]", ""))) {
                    if (out.length() > 0) out.append(' ');
                    out.append(o[i]);
                    from = i + 1;
                    break;
                }
            }
        }
        String t = out.length() > 0 ? out.toString() : foldedRest;
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private static Calendar date(Calendar today, int d, int month, String yearText) {
        Calendar c = (Calendar) today.clone();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.MONTH, Math.max(0, Math.min(11, month)));
        if (yearText != null) {
            int y = Integer.parseInt(yearText);
            c.set(Calendar.YEAR, y < 100 ? 2000 + y : y);
        } else if (c.get(Calendar.MONTH) < today.get(Calendar.MONTH)
                || (c.get(Calendar.MONTH) == today.get(Calendar.MONTH) && d < today.get(Calendar.DAY_OF_MONTH))) {
            c.add(Calendar.YEAR, 1); // a date already past this year means next year
        }
        c.set(Calendar.DAY_OF_MONTH, Math.max(1, Math.min(d, c.getActualMaximum(Calendar.DAY_OF_MONTH))));
        return c;
    }

    private static Calendar plus(Calendar c, int days) {
        Calendar d = (Calendar) c.clone();
        d.add(Calendar.DAY_OF_YEAR, days);
        return d;
    }

    private static Matcher find(StringBuilder s, String re) {
        Matcher m = Pattern.compile(re).matcher(s);
        return m.find() ? m : null;
    }

    /** Replaces a matched span with spaces so later patterns and the title skip it. */
    private static void blank(StringBuilder s, Matcher m) {
        for (int i = m.start(); i < m.end(); i++) s.setCharAt(i, ' ');
    }

    private static int indexOf(String[] a, String v) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i;
        return 0;
    }

    static String fold(String s) {
        String n = Normalizer.normalize(s.toLowerCase(new Locale("pt", "BR")), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "");
    }
}
