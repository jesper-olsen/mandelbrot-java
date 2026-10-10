# Mandelbrot in Java

This repository contains a Java (21+) implementation for generating visualizations of the Mandelbrot set.

The program can render the Mandelbrot set directly to the terminal as ASCII art or produce a data file for `gnuplot` to generate a high-resolution PNG image.

### Other Language Implementations

This project is part of a suite of mandelbrot implementations in different languages.

Single Thread/Multi-thread shows the number of seconds it takes to do a 5000x5000 calculation.

| Language    | Repository                                                           | Single Thread   | Multi-Thread | Simd | Multi-Thread + Simd |
| :--------   | :--------------------------------------------------------------------| ---------------:| -----------: | ----:| ------------------: |
| Awk         | [mandelbrot-awk](https://github.com/jesper-olsen/mandelbrot-awk)     |           417.9 |              |      |                     |
| Bash        | [mandelbrot-bash](https://github.com/jesper-olsen/mandelbrot-bash)   |         10187.3 |              |      |                     |
| C           | [mandelbrot-c](https://github.com/jesper-olsen/mandelbrot-c)         |             3.6 |          0.6 |  0.7 |               0.2   |
| C++         | [mandelbrot-cpp](https://github.com/jesper-olsen/mandelbrot-cpp)     |             3.7 |          0.6 |  0.7 |               0.2   |
| Erlang      | [mandelbrot_erl](https://github.com/jesper-olsen/mandelbrot_erl)     |            35.6 |          8.3 |      |                     |
| Fortran     | [mandelbrot-f](https://github.com/jesper-olsen/mandelbrot-f)         |             4.5 |              |      |                     |
| Go          | [mandelbrot-go](https://github.com/jesper-olsen/mandelbrot-go)       |             4.1 |          0.8 |  1.3 |               0.4   |
| **Java**    | [mandelbrot-java](https://github.com/jesper-olsen/mandelbrot-java)   |             3.9 |          0.8 |  1.4 |               0.5   |
| Lua         | [mandelbrot-lua](https://github.com/jesper-olsen/mandelbrot-lua)     |            33.2 |              |      |                     |
| Mojo        | [mandelbrot-mojo](https://github.com/jesper-olsen/mandelbrot-mojo)   |             3.8 |          1.2 |  0.7 |               0.4   |
| Nushell     | [mandelbrot-nu](https://github.com/jesper-olsen/mandelbrot-nu)       |         17186.6 |              |      |                     |
| Odin        | [mandelbrot-odin](https://github.com/jesper-olsen/mandelbrot-odin)   |             4.4 |              |      |                     |
| Python      | [mandelbrot-py](https://github.com/jesper-olsen/mandelbrot-py)       |     (pure) 93.3 | (jax)    5.9 |      |                     |
| R           | [mandelbrot-R](https://github.com/jesper-olsen/mandelbrot-R)         |           335.0 |              |      |                     |
| Rust        | [mandelbrot-rs](https://github.com/jesper-olsen/mandelbrot-rs)       |             4.7 |          1.3 |  1.4 |               0.8   |
| Swift       | [mandelbrot-swift](https://github.com/jesper-olsen/mandelbrot-swift) |             4.5 |          1.2 |  1.3 |               0.7   |
| Tcl         | [mandelbrot-tcl](https://github.com/jesper-olsen/mandelbrot-tcl)     |           306.9 |              |      |                     |
| Zig         | [mandelbrot-zig](https://github.com/jesper-olsen/mandelbrot-zig)     |             4.9 |          0.9 |  0.7 |               0.3   |

---

## Prerequisites

You will need the following installed:

1. A **JDK**, version 21 or later.
2. **Make** (optional, but recommended for easy building).
3. **Gnuplot** (required *only* for generating PNG images).

---

## Build

You can compile the program directly or use the provided Makefile.

**Option 1: Manual Compilation**

```
javac Mandelbrot.java
```

**Option 2: Using Make**

Simply run `make` in the project directory.

```
make
```

**Option 3: No build step**

Java 11+ can compile and run a single source file in one shot, which is convenient for quick experiments:

```
java Mandelbrot.java width=120 ll_x=-0.75 ll_y=0.1 ur_x=-0.74 ur_y=0.11
```

---

## Usage

The program can be configured via command-line arguments using a `key=value` format.

### 1. ASCII Art Output

To render the Mandelbrot set directly in your terminal, run:

```
java Mandelbrot
```

You can change the view and resolution by passing parameters:

```
# Zoom in on a different area with a wider view
java Mandelbrot width=120 ll_x=-0.75 ll_y=0.1 ur_x=-0.74 ur_y=0.11
```

### 2. PNG Image Generation

To create a high-resolution PNG, you first generate a data file and then process it with `gnuplot`.

**Step 1: Generate the data file**

Set `png=1` and specify the desired dimensions. Redirect the output to a file.

```
java Mandelbrot png=1 width=1000 height=750 > image.dat
```

**Step 2: Run gnuplot**

This will read `image.dat` and create `mandelbrot.png`.

```
gnuplot topng.gp
```

The result is a high-quality `mandelbrot.png` image.

![PNG Image of the Mandelbrot Set](mandelbrot.png)

## Performance

**Generating a 1000x750 data file:**
```sh
time java Mandelbrot png=1 width=1000 height=750 > image.dat
0.16s user 0.02s system 99% cpu 0.185 total
```

**Generating a 5000x5000 data file:**
```sh
time java Mandelbrot png=1 width=5000 height=5000 > image.dat
3.87s user 0.05s system 100% cpu 3.890 total
```

**Generating a 5000x5000 data file multiple worker threads**
```sh
time java MandelbrotThreaded png=1 width=5000 height=5000 > image.dat
image.dat  5.42s user 0.06s system 735% cpu 0.746 total
```

**Generating a 5000x5000 data file multiple worker threads**
``` sh
time java --add-modules jdk.incubator.vector MandelbrotSimdThreaded png=1 width=5000 height=5000 > image.dat
WARNING: Using incubator modules: jdk.incubator.vector
2.43s user 0.09s system 535% cpu 0.473 total
```

**Generating a 20000x20000 data file multiple worker threads**
``` sh
time java --add-modules jdk.incubator.vector MandelbrotSimdThreaded png=1 width=20000 height=20000 > image.dat
WARNING: Using incubator modules: jdk.incubator.vector
26.60s user 0.45s system 669% cpu 4.041 total
```

