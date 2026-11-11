package dev.velocity.client.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitor Wavefront OBJ mínimo e estrito para malhas de veículos.
 *
 * Suporta: v, vt, vn, o/g (nome da parte), usemtl (material), f com polígonos de 3+ vértices
 * nos formatos v, v/vt, v//vn e v/vt/vn, índices positivos e negativos. Ignora mtllib, s, l, p.
 * Polígonos com mais de 4 vértices são triangulados em leque (devem ser convexos).
 */
public final class ObjParser {
    /** Vértice resolvido: posição, UV e normal (normal NaN = calcular pela face). */
    public record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {}

    /** Polígono com 3 ou 4 vértices. */
    public record Face(Vertex[] vertices) {}

    /** parte → material → faces, preservando a ordem do arquivo. */
    public record Mesh(Map<String, Map<String, List<Face>>> parts, int faceCount) {}

    private ObjParser() {}

    public static Mesh parse(Reader in, String sourceName) throws IOException {
        List<float[]> pos = new ArrayList<>();
        List<float[]> uv = new ArrayList<>();
        List<float[]> nrm = new ArrayList<>();
        Map<String, Map<String, List<Face>>> parts = new LinkedHashMap<>();
        String part = "root";
        String material = "default";
        int faces = 0;
        int lineNo = 0;

        BufferedReader r = in instanceof BufferedReader b ? b : new BufferedReader(in);
        String line;
        while ((line = r.readLine()) != null) {
            lineNo++;
            line = line.strip();
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            String[] t = line.split("\\s+");
            try {
                switch (t[0]) {
                    case "v" -> pos.add(new float[]{f(t[1]), f(t[2]), f(t[3])});
                    case "vt" -> uv.add(new float[]{f(t[1]), t.length > 2 ? f(t[2]) : 0f});
                    case "vn" -> nrm.add(new float[]{f(t[1]), f(t[2]), f(t[3])});
                    case "o", "g" -> part = t.length > 1 ? t[1] : "root";
                    case "usemtl" -> material = t.length > 1 ? t[1] : "default";
                    case "f" -> {
                        int n = t.length - 1;
                        if (n < 3) throw new IOException("face com menos de 3 vértices");
                        Vertex[] vs = new Vertex[n];
                        for (int i = 0; i < n; i++) vs[i] = vertex(t[i + 1], pos, uv, nrm);
                        List<Face> list = parts.computeIfAbsent(part, k -> new LinkedHashMap<>())
                                .computeIfAbsent(material, k -> new ArrayList<>());
                        if (n <= 4) {
                            list.add(new Face(vs));
                            faces++;
                        } else {
                            for (int i = 1; i < n - 1; i++) {
                                list.add(new Face(new Vertex[]{vs[0], vs[i], vs[i + 1]}));
                                faces++;
                            }
                        }
                    }
                    default -> { /* mtllib, s, l, p etc.: ignorados */ }
                }
            } catch (RuntimeException | IOException e) {
                throw new IOException(sourceName + ":" + lineNo + ": " + e.getMessage(), e);
            }
        }
        return new Mesh(parts, faces);
    }

    private static Vertex vertex(String token, List<float[]> pos, List<float[]> uv, List<float[]> nrm) throws IOException {
        String[] idx = token.split("/", -1);
        float[] p = pick(pos, idx[0], "v");
        float[] t = idx.length > 1 && !idx[1].isEmpty() ? pick(uv, idx[1], "vt") : new float[]{0, 0};
        float[] n = idx.length > 2 && !idx[2].isEmpty() ? pick(nrm, idx[2], "vn") : new float[]{Float.NaN, Float.NaN, Float.NaN};
        // OBJ tem V para cima; texturas do Minecraft têm V para baixo.
        return new Vertex(p[0], p[1], p[2], t[0], 1f - t[1], n[0], n[1], n[2]);
    }

    private static float[] pick(List<float[]> list, String s, String kind) throws IOException {
        int i = Integer.parseInt(s);
        int index = i > 0 ? i - 1 : list.size() + i;
        if (index < 0 || index >= list.size()) throw new IOException("índice " + kind + " fora do intervalo: " + s);
        return list.get(index);
    }

    private static float f(String s) {
        return Float.parseFloat(s);
    }
}
