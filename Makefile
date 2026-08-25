JAVAC = javac
JAVACFLAGS = -Xlint:all -d .

SRC := Mandelbrot.java MandelbrotThreaded.java

.PHONY: all clean run run-pthread

all: build.stamp

build.stamp: $(SRC)
	$(JAVAC) $(JAVACFLAGS) $(SRC)
	touch build.stamp

run: all
	java Mandelbrot

run-pthread: all
	java MandelbrotThreaded

clean:
	rm -f *.class build.stamp
