package factorycraft.simulation.production;

import java.util.*;
import java.util.function.Predicate;

/** Independent eight-slot snapshot: four inputs/four outputs. Plans never mutate the live inventory. */
public final class BatchInventory<K> {
    public record Stack<K>(K key,int count,int limit) {
        public Stack { if(count<0 || limit<1 || count>0 && key==null)throw new IllegalArgumentException("Invalid item stack"); }
    }
    public record Ingredient<K>(Predicate<K> test,int count) {
        public Ingredient { Objects.requireNonNull(test);if(count<1)throw new IllegalArgumentException("Invalid ingredient count"); }
    }
    public static <K> List<Stack<K>> plan(List<Stack<K>> slots,List<Ingredient<K>> inputs,List<Stack<K>> outputs) {
        if(slots.size()!=8)throw new IllegalArgumentException("Expected four input and output slots");
        var result=new ArrayList<>(slots);
        for(var input:inputs) {
            int need=input.count();
            for(int s=0;s<4 && need>0;s++) {
                var stack=result.get(s);if(stack.count()==0 || !input.test().test(stack.key()))continue;
                int take=Math.min(need,stack.count());need-=take;result.set(s,new Stack<>(stack.key(),stack.count()-take,stack.limit()));
            }
            if(need>0)return null;
        }
        for(var output:outputs) {
            int need=output.count();
            for(int s=4;s<8 && need>0;s++) {
                var stack=result.get(s);if(stack.count()>0 && !Objects.equals(stack.key(),output.key()))continue;
                int limit=stack.count()==0 ? output.limit():Math.min(output.limit(),stack.limit());
                int take=Math.min(need,Math.max(0,limit-stack.count()));need-=take;
                if(take>0)result.set(s,new Stack<>(output.key(),stack.count()+take,limit));
            }
            if(need>0)return null;
        }
        return result;
    }
    private BatchInventory() {}
}
