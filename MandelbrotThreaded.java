/**
 * MandelbrotThreaded.java
 *
 * Generates Mandelbrot set visualizations in ASCII or for gnuplot,
 * using multiple worker threads with dynamic (atomic-counter) work
 * distribution across rows.
 *
 * A Java implementation for a cross-language comparison project.
 * It parses command-line arguments in the format key=value.
 *
 * Compilation:
 * javac MandelbrotThreaded.java
 *
 * Usage:
 * java MandelbrotThreaded
 * java MandelbrotThreaded width=120 ll_x=-0.75 ll_y=0.1 ur_x=-0.74 ur_y=0.11
 * java MandelbrotThreaded png=1 width=800 height=600 > mandelbrot.dat
 */

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

public final class MandelbrotThreaded {

    static final int NUM_THREADS = 9; // Adjust based on CPU core count
    static final int CHUNK_SIZE = 1;  // Number of rows to process per task

    /** Threads claim their next row from this counter - see threadMandelbrot. */
    static final AtomicInteger globalNextY = new AtomicInteger(0);

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
     *
     * @param value   the iteration value (0 to maxIter)
     * @param maxIter the maximum number of iterations
     * @return a character for visualization
     */
    static char cnt2char(int value, int maxIter) {
        int ns = SYMBOLS.length();
        int idx = (int) ((double) value / maxIter * (ns - 1));
        return SYMBOLS.charAt(idx);
    }

    /**
     * Calculates the escape time for a point in the complex plane.
     *
     * @param cr      the real part of the complex number c
     * @param ci      the imaginary part of the complex number c
     * @param maxIter the maximum number of iterations
     * @return an integer representing how close the point is to the set
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
     * Worker body: repeatedly claims the next unclaimed row (or CHUNK_SIZE
     * rows) via globalNextY, computes escape times, and writes them into
     * this row's slice of the shared output buffer. Rows are disjoint
     * across threads, so no synchronization is needed on the buffer itself.
     */
    static void threadMandelbrot(Config config, int[] buffer) {
        double fwidth = config.urX - config.llX;
        double fheight = config.urY - config.llY;

        while (true) {
            int yStart = globalNextY.getAndAdd(CHUNK_SIZE);
            if (yStart >= config.height) {
                break;
            }
            int yEnd = Math.min(yStart + CHUNK_SIZE, config.height);

            for (int y = yStart; y < yEnd; ++y) {
                double imag = config.urY - y * fheight / config.height;
                int rowOffset = y * config.width;
                for (int x = 0; x < config.width; ++x) {
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
            // Gnuplot output (Y reversed)
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
            // ASCII output (standard Y direction)
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
