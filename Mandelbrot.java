/**
 * Mandelbrot.java
 *
 * Generates Mandelbrot set visualizations in ASCII or for gnuplot.
 *
 * A Java implementation for a cross-language comparison project.
 * It parses command-line arguments in the format key=value.
 *
 * Compilation:
 * javac Mandelbrot.java
 *
 * Usage:
 * java Mandelbrot
 * java Mandelbrot width=120 ll_x=-0.75 ll_y=0.1 ur_x=-0.74 ur_y=0.11
 * java Mandelbrot png=1 width=800 height=600 > mandelbrot.dat
 */

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class Mandelbrot {

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
        // Map the value [0, maxIter] to an index [0, ns-1]
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
     * Renders the Mandelbrot set as ASCII art to stdout.
     */
    static void asciiOutput(Config config, OutputStream out) throws IOException {
        double fwidth = config.urX - config.llX;
        double fheight = config.urY - config.llY;
        byte[] row = new byte[config.width + 1];
        row[config.width] = '\n';

        for (int y = 0; y < config.height; ++y) {
            double imag = config.urY - y * fheight / config.height;
            for (int x = 0; x < config.width; ++x) {
                double real = config.llX + x * fwidth / config.width;
                int iter = escapeTime(real, imag, config.maxIter);
                row[x] = (byte) cnt2char(iter, config.maxIter);
            }
            out.write(row);
        }
    }

    /**
     * Generates text output suitable for gnuplot to stdout.
     */
    static void gptextOutput(Config config, OutputStream out) throws IOException {
        double fwidth = config.urX - config.llX;
        double fheight = config.urY - config.llY;
        // One row buffer, reused; sized generously (max 3 digits + ", " per cell).
        byte[] buffer = new byte[config.width * 6 + 16];

        for (int y = config.height - 1; y >= 0; --y) {
            double imag = config.urY - y * fheight / config.height;
            int p = 0;
            for (int x = 0; x < config.width; ++x) {
                double real = config.llX + x * fwidth / config.width;
                int iter = escapeTime(real, imag, config.maxIter);

                if (x > 0) {
                    buffer[p++] = ',';
                    buffer[p++] = ' ';
                }
                // Manual itoa - iter is always in [0, maxIter], so at most 3 digits
                // for the typical 0-255 range, but handle arbitrary widths safely.
                if (iter >= 100) {
                    buffer[p++] = (byte) ('0' + (iter / 100));
                    iter %= 100;
                    buffer[p++] = (byte) ('0' + (iter / 10));
                    buffer[p++] = (byte) ('0' + (iter % 10));
                } else if (iter >= 10) {
                    buffer[p++] = (byte) ('0' + (iter / 10));
                    buffer[p++] = (byte) ('0' + (iter % 10));
                } else {
                    buffer[p++] = (byte) ('0' + iter);
                }
            }
            buffer[p++] = '\n';
            out.write(buffer, 0, p);
        }
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

    public static void main(String[] args) throws IOException {
        Config config = new Config();
        for (String arg : args) {
            parseArg(arg, config);
        }

        OutputStream out = new BufferedOutputStream(System.out, 1 << 16);
        if (config.png) {
            gptextOutput(config, out);
        } else {
            asciiOutput(config, out);
        }
        out.flush();
    }
}
