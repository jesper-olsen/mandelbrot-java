/**
 * MandelbrotSimdThreaded.java
 *
 * Generates Mandelbrot set visualizations in ASCII or for gnuplot, using
 * the (incubating) Vector API for SIMD and multiple worker threads with
 * dynamic (atomic-counter) work distribution across rows.
 *
 * A Java implementation for a cross-language comparison project, analogous
 * to mandelbrot_simd_pthread_v8.c.
 *
 * The SIMD kernel uses float32 instead of the scalar path's float64, which
 * gives a small fraction of pixels an iteration count off by more than one
 * compared to the scalar/double-precision result - a precision tradeoff,
 * not a bug (same tradeoff the C SIMD kernel makes).
 *
 * The Vector API is still incubating as of JDK 26 (eleventh round, JEP 529)
 * - it needs an explicit module flag at both compile and run time:
 *
 * Compilation:
 * javac --add-modules jdk.incubator.vector --release 21 MandelbrotSimdThreaded.java
 *
 * Usage:
 * java --add-modules jdk.incubator.vector MandelbrotSimdThreaded
 * java --add-modules jdk.incubator.vector MandelbrotSimdThreaded width=120 ll_x=-0.75 ll_y=0.1 ur_x=-0.74 ur_y=0.11
 * java --add-modules jdk.incubator.vector MandelbrotSimdThreaded png=1 width=800 height=600 > mandelbrot.dat
 */

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

public final class MandelbrotSimdThreaded {

    static final int NUM_THREADS = 9; // Adjust based on CPU core count
    static final int CHUNK_SIZE = 1;  // Number of rows to process per task

    static final AtomicInteger globalNextY = new AtomicInteger(0);

    // Widest float32/int32 vector shape this JVM's chosen preferred for the
    // current CPU (e.g. 4 lanes on Apple Silicon NEON, 8 on x86 AVX2,
    // 16 on AVX-512). Unlike the C version's hardcoded 8-wide vectors, this
    // adapts to whatever the hardware actually offers.
    static final VectorSpecies<Float> FSPECIES = FloatVector.SPECIES_PREFERRED;
    static final VectorSpecies<Integer> ISPECIES = IntVector.SPECIES_PREFERRED;
    static final int LANES = FSPECIES.length();

    /** Command-line-configurable view/render parameters. */
    static final class Config {
        int width = 100;
        int height = 75;
        boolean png = false;
        double llX = -1.2;
        double llY = 0.20;
        double urX = -1.0;
        double urY = 0.35;
        int maxIter = 255;
    }

    private static final String SYMBOLS = "MW2a_. ";

    /**
     * Maps an iteration count to an ASCII character.
     */
    static char cnt2char(int value, int maxIter) {
        int ns = SYMBOLS.length();
        int idx = (int) ((double) value / maxIter * (ns - 1));
        return SYMBOLS.charAt(idx);
    }

    /**
     * Calculates the escape time for a single point in the complex plane
     * (double precision) - used for the scalar remainder columns.
     */
    static int escapeTime(double cr, double ci, int maxIter) {
        double zr = 0.0, zi = 0.0;
        int iter;
        for (iter = 0; iter < maxIter; ++iter) {
            double zr2 = zr * zr;
            double zi2 = zi * zi;
            if (zr2 + zi2 > 4.0) {
                break;
            }
            double tmp = zr2 - zi2 + cr;
            zi = 2.0 * zr * zi + ci;
            zr = tmp;
        }
        return maxIter - iter;
    }

    /**
     * Calculates the escape time for LANES points at once (float32).
     *
     * Can't use a data-dependent early exit per lane the way the scalar
     * version does - instead every lane keeps iterating, and a mask
     * (active) stops lanes from accumulating further iterations once
     * they've escaped, until either every lane has escaped or maxIter is
     * reached. Same approach as escape_time_simd8 in the C SIMD port.
     */
    static IntVector escapeTimeSimd(FloatVector cr, FloatVector ci, int maxIter) {
        FloatVector zr = FloatVector.zero(FSPECIES);
        FloatVector zi = FloatVector.zero(FSPECIES);
        IntVector iters = IntVector.zero(ISPECIES);
        IntVector ones = IntVector.broadcast(ISPECIES, 1);
        VectorMask<Float> active = FSPECIES.maskAll(true);

        for (int i = 0; i < maxIter; ++i) {
            FloatVector zr2 = zr.mul(zr);
            FloatVector zi2 = zi.mul(zi);
            VectorMask<Float> still = zr2.add(zi2).compare(VectorOperators.LE, 4.0f);
            active = active.and(still);
            if (!active.anyTrue()) {
                break;
            }

            FloatVector tmp = zr2.sub(zi2).add(cr);
            zi = zr.mul(zi).mul(2.0f).add(ci);
            zr = tmp;

            VectorMask<Integer> activeI = active.cast(ISPECIES);
            iters = iters.add(ones, activeI);
        }

        IntVector maxIterV = IntVector.broadcast(ISPECIES, maxIter);
        return maxIterV.sub(iters);
    }

    /**
     * Parses a single "key=value" command-line argument.
     */
    static void parseArg(String arg, Config config) {
        int eq = arg.indexOf('=');
        if (eq < 0) {
            System.err.println("Warning: Ignoring invalid argument '" + arg + "'");
            return;
        }
        String key = arg.substring(0, eq);
        String value = arg.substring(eq + 1);

        try {
            switch (key) {
                case "width" -> config.width = Integer.parseInt(value);
                case "height" -> config.height = Integer.parseInt(value);
                case "png" -> config.png = Integer.parseInt(value) != 0;
                case "ll_x" -> config.llX = Double.parseDouble(value);
                case "ll_y" -> config.llY = Double.parseDouble(value);
                case "ur_x" -> config.urX = Double.parseDouble(value);
                case "ur_y" -> config.urY = Double.parseDouble(value);
                case "max_iter" -> config.maxIter = Integer.parseInt(value);
                default -> System.err.println("Warning: Unknown parameter '" + key + "'");
            }
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid value for '" + key + "': '" + value + "'");
        }
    }

    /**
     * Worker body: claims rows dynamically via globalNextY, computes escape
     * times LANES columns at a time via the vectorized kernel (with a
     * scalar remainder for columns that don't fill a full vector), and
     * writes into this row's slice of the shared output buffer.
     */
    static void threadMandelbrot(Config config, int[] buffer) {
        double fwidth = config.urX - config.llX;
        double fheight = config.urY - config.llY;
        float[] crScratch = new float[LANES];
        int[] valsScratch = new int[LANES];

        while (true) {
            int yStart = globalNextY.getAndAdd(CHUNK_SIZE);
            if (yStart >= config.height) {
                break;
            }
            int yEnd = Math.min(yStart + CHUNK_SIZE, config.height);

            for (int y = yStart; y < yEnd; ++y) {
                double imag = config.urY - y * fheight / config.height;
                float imagF = (float) imag;
                int rowOffset = y * config.width;
                int x = 0;

                for (; x + LANES <= config.width; x += LANES) {
                    for (int lane = 0; lane < LANES; ++lane) {
                        crScratch[lane] = (float) (config.llX + (x + lane) * fwidth / config.width);
                    }
                    FloatVector cr = FloatVector.fromArray(FSPECIES, crScratch, 0);
                    FloatVector ci = FloatVector.broadcast(FSPECIES, imagF);
                    IntVector vals = escapeTimeSimd(cr, ci, config.maxIter);
                    vals.intoArray(valsScratch, 0);
                    System.arraycopy(valsScratch, 0, buffer, rowOffset + x, LANES);
                }

                // Remainder columns that don't fill a full vector.
                for (; x < config.width; ++x) {
                    double real = config.llX + x * fwidth / config.width;
                    buffer[rowOffset + x] = escapeTime(real, imag, config.maxIter);
                }
            }
        }
    }

    /**
     * Writes the precomputed result buffer as ASCII art or gnuplot text.
     */
    static void finalOutput(Config config, int[] resultBuffer, OutputStream out) throws IOException {
        int width = config.width;

        if (config.png) {
            byte[] rowBuf = new byte[width * 6 + 16];
            for (int y = config.height - 1; y >= 0; --y) {
                int rowOffset = y * width;
                int p = 0;
                for (int x = 0; x < width; ++x) {
                    int iter = resultBuffer[rowOffset + x];
                    if (x > 0) {
                        rowBuf[p++] = ',';
                        rowBuf[p++] = ' ';
                    }
                    if (iter >= 100) {
                        rowBuf[p++] = (byte) ('0' + (iter / 100));
                        iter %= 100;
                        rowBuf[p++] = (byte) ('0' + (iter / 10));
                        rowBuf[p++] = (byte) ('0' + (iter % 10));
                    } else if (iter >= 10) {
                        rowBuf[p++] = (byte) ('0' + (iter / 10));
                        rowBuf[p++] = (byte) ('0' + (iter % 10));
                    } else {
                        rowBuf[p++] = (byte) ('0' + iter);
                    }
                }
                rowBuf[p++] = '\n';
                out.write(rowBuf, 0, p);
            }
        } else {
            byte[] rowBuf = new byte[width + 1];
            rowBuf[width] = '\n';
            for (int y = 0; y < config.height; ++y) {
                int rowOffset = y * width;
                for (int x = 0; x < width; ++x) {
                    rowBuf[x] = (byte) cnt2char(resultBuffer[rowOffset + x], config.maxIter);
                }
                out.write(rowBuf);
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Config config = new Config();
        for (String arg : args) {
            parseArg(arg, config);
        }

        int totalPixels = config.width * config.height;
        int[] resultBuffer = new int[totalPixels];

        Thread[] threads = new Thread[NUM_THREADS];
        for (int i = 0; i < NUM_THREADS; ++i) {
            threads[i] = new Thread(() -> threadMandelbrot(config, resultBuffer));
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }

        OutputStream out = new BufferedOutputStream(System.out, 1 << 16);
        finalOutput(config, resultBuffer, out);
        out.flush();
    }
}
