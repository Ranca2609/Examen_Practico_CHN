package gt.gob.chn.prestamos.infrastructure.adapter.out.reporte;

import java.awt.Color;

// Color de AWT porque OpenPDF lo consume directo; es solo un valor, no requiere entorno gráfico.
final class PaletaReporte {

    static final Color AZUL_INSTITUCIONAL = new Color(0x0B, 0x4F, 0x8A);

    static final Color AZUL_OSCURO = new Color(0x07, 0x37, 0x61);

    static final Color GRIS_SEPARADOR = new Color(0x9A, 0xA3, 0xAF);

    static final Color GRIS_FILA_ALTERNA = new Color(0xF3, 0xF6, 0xF9);

    static final Color GRIS_BLOQUE_DATOS = new Color(0xED, 0xF1, 0xF6);

    static final Color GRIS_TEXTO = new Color(0x55, 0x5F, 0x6D);

    static final Color NEGRO_TEXTO = new Color(0x1C, 0x1C, 0x1C);

    static final Color BLANCO = Color.WHITE;

    static final float TAM_SIGLAS = 14f;

    static final float TAM_ENTIDAD = 10f;

    static final float TAM_SISTEMA = 7.5f;

    static final float TAM_TITULO = 12f;

    static final float TAM_SUBTITULO = 9f;

    static final float TAM_DATO = 8f;

    static final float TAM_TABLA = 7.5f;

    static final float TAM_PIE = 7f;

    static final String SIGLAS = "CHN";

    static final String ENTIDAD = "Crédito Hipotecario Nacional de Guatemala";

    static final String SISTEMA = "Sistema de Gestión de Préstamos";

    private PaletaReporte() {
    }

    // POI no entiende java.awt.Color: XSSFColor necesita los bytes RGB.
    static byte[] rgb(Color color) {
        return new byte[] {
                (byte) color.getRed(),
                (byte) color.getGreen(),
                (byte) color.getBlue()
        };
    }
}
