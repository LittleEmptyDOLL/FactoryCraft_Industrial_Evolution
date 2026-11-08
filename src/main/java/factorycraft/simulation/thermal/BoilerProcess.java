package factorycraft.simulation.thermal;

import factorycraft.simulation.fluids.FluidCell;

/** Progress and unspent fuel heat are persistent; a blocked recipe never burns another tick. */
public final class BoilerProcess {
    private int fuel,progress;
    private String recipe="";
    public int fuel() { return fuel; }
    public int progress() { return progress; }
    public String recipe() { return recipe; }
    public void restore(int fuel,int progress,String recipe) {
        if(fuel<0 || progress<0 || recipe==null)throw new IllegalArgumentException("Invalid boiler state");
        this.fuel=fuel;this.progress=progress;this.recipe=recipe;
    }
    public void refuel(int ticks) { if(ticks<1 || fuel!=0)throw new IllegalArgumentException("Invalid refuel");fuel=ticks; }
    public <K> boolean advance(String identity,int ticks,FluidCell<K> input,int amount,FluidCell<K> output,K result,int produced) {
        if(ticks<1 || amount<1 || produced<1)throw new IllegalArgumentException("Invalid conversion");
        if(fuel==0 || input.amount()<amount || output.space()<produced || !output.accepts(result))return false;
        if(!recipe.equals(identity)) { recipe=identity;progress=0; }
        fuel--;progress++;
        if(progress>=ticks) {
            progress=0;recipe="";
            if(!input.convertTo(output,amount,result,produced,false))throw new IllegalStateException("Validated conversion failed");
        }
        return true;
    }
}
