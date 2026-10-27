package com.pelayo.autoescuela.app;

import com.badlogic.gdx.graphics.Color;

/**
 * Colores oficiales de la gama Clio III (fase 1, 2005-2009) con su nombre de
 * catálogo y código de pintura Renault, para elegir la carrocería en Ajustes.
 * Los tonos RGB son aproximaciones a la vista (Renault no publica valores
 * RGB); {@link #ORIGINAL} deja el gris del modelo 3D.
 */
public enum CarPaint {
    ORIGINAL("Gris del modelo", "", Finish.METALLIC, 0x727372),
    BLANC_GLACIER("Blanc Glacier", "369", Finish.SOLID, 0xEDEDE8),
    GRIS_PLATINE("Gris Platine", "D69", Finish.METALLIC, 0xAEB0B2),
    GRIS_CASSIOPEE("Gris Cassiopée", "KNG", Finish.METALLIC, 0x86888A),
    GRIS_ECLIPSE("Gris Éclipse", "B66", Finish.METALLIC, 0x5F6266),
    NOIR_NACRE("Noir Nacré", "676", Finish.PEARL, 0x16171A),
    ROUGE_VIF("Rouge Vif", "727", Finish.SOLID, 0xC4141F),
    ROUGE_DYNA("Rouge Dyna", "NNJ", Finish.METALLIC, 0xA3141C),
    ROUGE_VOLCAN("Rouge Volcan", "D75", Finish.METALLIC, 0x84191C),
    BLEU_ICEBERG("Bleu Iceberg", "RNK", Finish.PEARL, 0x9FBAD3),
    BLEU_EXTREME("Bleu Extrême", "RNA", Finish.PEARL, 0x2C6BB3),
    BLEU_GRIS("Bleu Gris", "J47", Finish.METALLIC, 0x5D7385),
    BLEU_ROI("Bleu Roi", "460", Finish.SOLID, 0x203E8C),
    BLEU_METHYL("Bleu Méthyl", "432", Finish.PEARL, 0x1D3770),
    VERT_CUIVRE("Vert Cuivre", "D97", Finish.METALLIC, 0x5A5E44),
    BRONZE_DORE("Bronze Doré", "D18", Finish.METALLIC, 0x8A7352),
    BEIGE_POIVRE("Beige Poivre", "D11", Finish.METALLIC, 0xB3A487);

    /** Acabado de la pintura: cambia el brillo metálico del material. */
    public enum Finish {
        SOLID(0f, 0.35f), METALLIC(0.65f, 0.30f), PEARL(0.55f, 0.22f);

        public final float metallic;
        public final float roughness;

        Finish(float metallic, float roughness) {
            this.metallic = metallic;
            this.roughness = roughness;
        }
    }

    /** Nombre del catálogo Renault (francés, como en la etiqueta del coche). */
    public final String officialName;
    /** Código de pintura Renault (vacío en ORIGINAL). */
    public final String code;
    public final Finish finish;
    /** Color de la muestra, en sRGB. */
    private final Color srgb;

    CarPaint(String officialName, String code, Finish finish, int rgb) {
        this.officialName = officialName;
        this.code = code;
        this.finish = finish;
        this.srgb = new Color(((rgb >> 16) & 0xff) / 255f, ((rgb >> 8) & 0xff) / 255f, (rgb & 0xff) / 255f, 1f);
    }

    /** Color de la muestra en la interfaz (sRGB). */
    public Color swatch() {
        return srgb;
    }

    /**
     * Color base para el material PBR: los factores de glTF van en espacio
     * lineal, así que se convierte el sRGB de la muestra.
     */
    public Color linear() {
        return new Color(toLinear(srgb.r), toLinear(srgb.g), toLinear(srgb.b), 1f);
    }

    private static float toLinear(float c) {
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
    }

    public static CarPaint parse(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ORIGINAL;
        }
    }
}
