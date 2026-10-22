package br.sinosdomundo.pack;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * O modelo do sino feito no Blockbench, separado em corpo e badalo (peças com nome começando por
 * "badalo"), e a geometria que a animação precisa: onde fica o eixo (topo da coroa) e em que
 * ângulo o badalo encosta na borda.
 */
public record BellModel(JsonObject body, JsonObject clapper, double pivotY, double contactAngle) {

    /** Modelo sem badalo: a animação balança só o sino. */
    public boolean hasClapper() {
        return clapper != null;
    }

    public static BellModel parse(InputStream in) {
        JsonObject model = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        JsonArray bodyElements = new JsonArray();
        JsonArray clapperElements = new JsonArray();
        for (JsonElement element : model.getAsJsonArray("elements")) {
            JsonObject e = element.getAsJsonObject();
            String name = e.has("name") ? e.get("name").getAsString().toLowerCase(Locale.ROOT) : "";
            (name.startsWith("badalo") ? clapperElements : bodyElements).add(e);
        }

        // Eixo: topo da peça mais alta do corpo (a coroa)
        double pivotY = 0;
        for (JsonElement e : bodyElements) {
            pivotY = Math.max(pivotY, to(e, 1));
        }

        double contact = 0;
        if (!clapperElements.isEmpty()) {
            // Ponta do badalo: a peça mais baixa; a borda: a peça mais baixa do corpo
            JsonObject ball = lowest(clapperElements);
            JsonObject rim = lowest(bodyElements);
            double ballRadius = (to(ball, 0) - from(ball, 0)) / 2;
            double ballCenterY = (from(ball, 1) + to(ball, 1)) / 2;
            double rimHalfWidth = (to(rim, 0) - from(rim, 0)) / 2;
            double length = pivotY - ballCenterY;
            double sin = Math.min(1, Math.max(0, (rimHalfWidth - ballRadius) / length));
            contact = Math.toDegrees(Math.asin(sin));
        }

        JsonObject body = withElements(model, bodyElements);
        JsonObject clapper = clapperElements.isEmpty() ? null : withElements(model, clapperElements);
        return new BellModel(body, clapper, pivotY, contact);
    }

    private static JsonObject withElements(JsonObject model, JsonArray elements) {
        JsonObject copy = model.deepCopy();
        copy.add("elements", elements);
        copy.remove("groups");
        return copy;
    }

    private static JsonObject lowest(JsonArray elements) {
        JsonObject lowest = null;
        for (JsonElement e : elements) {
            if (lowest == null || from(e, 1) < from(lowest, 1)) {
                lowest = e.getAsJsonObject();
            }
        }
        return lowest;
    }

    private static double from(JsonElement e, int axis) {
        return e.getAsJsonObject().getAsJsonArray("from").get(axis).getAsDouble();
    }

    private static double to(JsonElement e, int axis) {
        return e.getAsJsonObject().getAsJsonArray("to").get(axis).getAsDouble();
    }
}
