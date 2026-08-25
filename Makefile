JAVAC = javac
JAVACFLAGS = -Xlint:all -d .
VECTOR_FLAGS = --add-modules jdk.incubator.vector

SRC := Mandelbrot.java MandelbrotThreaded.java
SIMD_SRC := MandelbrotSimdThreaded.java

.PHONY: all clean run run-pthread run-simd

all: build.stamp simd.stamp

build.stamp: $(SRC)
	$(JAVAC) $(JAVACFLAGS) $(SRC)
	touch build.stamp

simd.stamp: $(SIMD_SRC)
	$(JAVAC) $(JAVACFLAGS) $(VECTOR_FLAGS) $(SIMD_SRC)
	touch simd.stamp

run: build.stamp
	java Mandelbrot

run-pthread: build.stamp
	java MandelbrotThreaded

run-simd: simd.stamp
	java $(VECTOR_FLAGS) MandelbrotSimdThreaded

clean:
	rm -f *.class build.stamp simd.stamp
