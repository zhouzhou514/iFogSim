package org.fog.utils.distribution;

public class UniformDistribution extends Distribution{

	private double min;
	private double max;
	
	@Override
	public double getNextValue() {
		return getRandom().nextDouble()*(getMax()-getMin())+getMin();
	}

	public double getMin() {
		return min;
	}

	public void setMin(double min) {
		this.min = min;
	}

	public double getMax() {
		return max;
	}

	public void setMax(double max) {
		this.max = max;
	}

}