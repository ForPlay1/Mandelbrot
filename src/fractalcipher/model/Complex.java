package fractalcipher.model;

public class Complex {
    public final double re;
    public final double im;

    public Complex(double re, double im) {
        this.re = re;
        this.im = im;
    }

    public Complex add(Complex other) {
        return new Complex(this.re + other.re, this.im + other.im);
    }

    public Complex multiply(Complex other) {
        return new Complex(
                this.re * other.re - this.im * other.im,
                this.re * other.im + this.im * other.re
        );
    }

    public double absSquared() {
        return re * re + im * im;
    }
}