package com.intellectualcrafters.plot.object;

import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.mutable.MutableInt;

import com.intellectualcrafters.plot.PS;
import com.intellectualcrafters.plot.flag.Flag;
import com.intellectualcrafters.plot.flag.FlagManager;
import com.intellectualcrafters.plot.generator.BukkitHybridUtils;
import com.intellectualcrafters.plot.util.TaskManager;

public class PlotAnalysis {
    public int changes;
    public int faces;
    public int data;
    public int air;
    public int variety;
    
    public int changes_sd;
    public int faces_sd;
    public int data_sd;
    public int air_sd;
    public int variety_sd;
    
    public double complexity;
    
    public static PlotAnalysis MODIFIERS = new PlotAnalysis();

    public static PlotAnalysis getAnalysis(Plot plot) {
        Flag flag = FlagManager.getPlotFlag(plot, "analysis");
        if (flag != null) {
            PlotAnalysis analysis = new PlotAnalysis();
            List<Integer> values = (List<Integer>) flag.getValue();
            analysis.changes = values.get(0);
            analysis.faces = values.get(1);
            analysis.data = values.get(2);
            analysis.air = values.get(3);
            analysis.variety = values.get(4);
            
            analysis.changes_sd = values.get(5);
            analysis.faces_sd = values.get(6);
            analysis.data_sd = values.get(7);
            analysis.air_sd = values.get(8);
            analysis.variety_sd = values.get(9);
            
            analysis.complexity =
            + (analysis.changes) * MODIFIERS.changes
            + (analysis.faces) * MODIFIERS.faces
            + (analysis.data) * MODIFIERS.data
            + (analysis.air) * MODIFIERS.air
            + (analysis.variety) * MODIFIERS.variety
            + (analysis.changes_sd) * MODIFIERS.changes_sd
            + (analysis.faces_sd) * MODIFIERS.faces_sd
            + (analysis.data_sd) * MODIFIERS.data_sd
            + (analysis.air_sd) * MODIFIERS.air_sd
            + (analysis.variety_sd) * MODIFIERS.variety_sd
            ;
            return analysis;
        }
        return null;
    }
    
    public static void analyzePlot(Plot plot, RunnableVal<PlotAnalysis> whenDone) {
        PlotAnalysis analysis = getAnalysis(plot);
        if (analysis != null) {
            whenDone.value = analysis;
            if (whenDone != null) whenDone.run();
            return;
        }
        BukkitHybridUtils.manager.analyzePlot(plot, whenDone);
    }
    
    public static boolean running = false;
    
    /**
     * This will set the optimal modifiers for the plot analysis based on the current plot ratings<br>
     *  - Will be used to calibrate the threshold for plot clearing
     * @param whenDone
     */
    public static void calcOptimalModifiers(final Runnable whenDone) {
        if (running) {
            PS.log("Calibration task already in progress!");
            return;
        }
        running = true;
        PS.log(" - Fetching all plots");
        final ArrayList<Plot> plots = new ArrayList<>(PS.get().getPlots());
        TaskManager.runTaskAsync(new Runnable() {
            @Override
            public void run() {
                Iterator<Plot> iter = plots.iterator();

                PS.log(" - $1Reducing " + plots.size() + " plots to those with sufficient data");
                while (iter.hasNext()) {
                    Plot plot = iter.next();
                    if (plot.settings.ratings == null || plot.settings.ratings.size() == 0) {
                        iter.remove();
                    }
                }
                PS.log(" - | Reduced to " + plots.size() + " plots");
                
                if (plots.size() < 3) {
                    PS.log("Calibration cancelled due to insufficient comparison data, please try again later");
                    running = false;
                    return;
                }
                
                PS.log(" - $1Analyzing plot contents (this may take a while)");
                
                final int[] changes = new int[plots.size()];
                final int[] faces = new int[plots.size()];
                final int[] data = new int[plots.size()];
                final int[] air = new int[plots.size()];
                final int[] variety = new int[plots.size()];
                
                final int[] changes_sd = new int[plots.size()];
                final int[] faces_sd = new int[plots.size()];
                final int[] data_sd = new int[plots.size()];
                final int[] air_sd = new int[plots.size()];
                final int[] variety_sd = new int[plots.size()];
                
                final int[] ratings = new int[plots.size()];
                
                final MutableInt mi = new MutableInt(0);
                
                Thread ratingAnalysis = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        for (;mi.intValue() < plots.size(); mi.increment()) {
                            int i = mi.intValue();
                            Plot plot = plots.get(i);
                            ratings[i] = (int) ((plot.getAverageRating() + plot.settings.ratings.size()) * 100);
                            PS.log(" | " + plot + " (rating) " + (ratings[i]));
                        }
                    }
                });
                ratingAnalysis.start();
                
                final ArrayDeque<Plot> plotsQueue = new ArrayDeque<>(plots);
                Plot queuePlot;
                while ((queuePlot = plotsQueue.poll()) != null) {
                    PS.log(" | " + queuePlot);
                    final Thread thread = Thread.currentThread();
                    analyzePlot(queuePlot, new RunnableVal<PlotAnalysis>() {
                        public void run() {
                            thread.notify();
                        }
                    });
                    try {
                        thread.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                
                PS.log(" - $1Waiting on plot rating thread: " + ((mi.intValue() * 100) / plots.size()) + "%");
                try {
                    ratingAnalysis.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
                PS.log(" - $1Processing and grouping single plot analysis for bulk processing");
                for (int i = 0; i < plots.size(); i++) {
                    Plot plot = plots.get(i);
                    PS.log(" | " + plot);
                    PlotAnalysis analysis = plot.getComplexity();
                  
                    changes[i] = analysis.changes;
                    faces[i] = analysis.faces;
                    data[i] = analysis.data;
                    air[i] = analysis.air;
                    variety[i] = analysis.variety;
                    
                    changes_sd[i] = analysis.changes_sd;
                    faces_sd[i] = analysis.faces_sd;
                    data_sd[i] = analysis.data_sd;
                    air_sd[i] = analysis.air_sd;
                    variety_sd[i] = analysis.variety_sd;
                }
                
                int[] rank_ratings = rank(ratings);
                int n = rank_ratings.length;
                
                PS.log(" - $1Calculating rank correlation: ");
                PS.log(" - The analyzed plots which were processed and put into bulk data will be compared and correlated to the ranked plots");
                PS.log(" - The calculated correlation constant will be used to calibrate the threshold for auto plot clearing");
                
                int[] rank_changes = rank(changes);
                int[] sd_changes = getSD(rank_changes);
                int[] variance_changes = square(sd_changes);
                int sum_changes = sum(variance_changes);
                double factor_changes = getCC(n, sum_changes);
                PlotAnalysis.MODIFIERS.changes = (int) (factor_changes * 100);
                PS.log(" - | changes " + factor_changes);
                
                int[] rank_faces = rank(faces);
                int[] sd_faces = getSD(rank_faces);
                int[] variance_faces = square(sd_faces);
                int sum_faces = sum(variance_faces);
                double factor_faces = getCC(n, sum_faces);
                PlotAnalysis.MODIFIERS.faces = (int) (factor_faces * 100);
                PS.log(" - | faces " + factor_faces);
                
                int[] rank_data = rank(data);
                int[] sd_data = getSD(rank_data);
                int[] variance_data = square(sd_data);
                int sum_data = sum(variance_data);
                double factor_data = getCC(n, sum_data);
                PlotAnalysis.MODIFIERS.data = (int) (factor_data * 100);
                PS.log(" - | data " + factor_data);
                
                int[] rank_air = rank(air);
                int[] sd_air = getSD(rank_air);
                int[] variance_air = square(sd_air);
                int sum_air = sum(variance_air);
                double factor_air = getCC(n, sum_air);
                PlotAnalysis.MODIFIERS.air = (int) (factor_air * 100);
                PS.log(" - | air " + factor_air);
                
                int[] rank_variety = rank(variety);
                int[] sd_variety = getSD(rank_variety);
                int[] variance_variety = square(sd_variety);
                int sum_variety = sum(variance_variety);
                double factor_variety = getCC(n, sum_variety);
                PlotAnalysis.MODIFIERS.variety = (int) (factor_variety * 100);
                PS.log(" - | variety " + factor_variety);
                
                int[] rank_changes_sd = rank(changes_sd);
                int[] sd_changes_sd = getSD(rank_changes_sd);
                int[] variance_changes_sd = square(sd_changes_sd);
                int sum_changes_sd = sum(variance_changes_sd);
                double factor_changes_sd = getCC(n, sum_changes_sd);
                PlotAnalysis.MODIFIERS.changes_sd = (int) (factor_changes_sd * 100);
                PS.log(" - | changes_sd " + factor_changes_sd);
                
                int[] rank_faces_sd = rank(faces_sd);
                int[] sd_faces_sd = getSD(rank_faces_sd);
                int[] variance_faces_sd = square(sd_faces_sd);
                int sum_faces_sd = sum(variance_faces_sd);
                double factor_faces_sd = getCC(n, sum_faces_sd);
                PlotAnalysis.MODIFIERS.faces_sd = (int) (factor_faces_sd * 100);
                PS.log(" - | faces_sd " + factor_faces_sd);
                
                int[] rank_data_sd = rank(data_sd);
                int[] sd_data_sd = getSD(rank_data_sd);
                int[] variance_data_sd = square(sd_data_sd);
                int sum_data_sd = sum(variance_data_sd);
                double factor_data_sd = getCC(n, sum_data_sd);
                PlotAnalysis.MODIFIERS.data_sd = (int) (factor_data_sd * 100);
                PS.log(" - | data_sd " + factor_data_sd);
                
                int[] rank_air_sd = rank(air_sd);
                int[] sd_air_sd = getSD(rank_air_sd);
                int[] variance_air_sd = square(sd_air_sd);
                int sum_air_sd = sum(variance_air_sd);
                double factor_air_sd = getCC(n, sum_air_sd);
                PlotAnalysis.MODIFIERS.air_sd = (int) (factor_air_sd * 100);
                PS.log(" - | air_sd " + factor_air_sd);
                
                int[] rank_variety_sd = rank(variety_sd);
                int[] sd_variety_sd = getSD(rank_variety_sd);
                int[] variance_variety_sd = square(sd_variety_sd);
                int sum_variety_sd = sum(variance_variety_sd);
                double factor_variety_sd = getCC(n, sum_variety_sd);
                PlotAnalysis.MODIFIERS.variety_sd = (int) (factor_variety_sd * 100);
                PS.log(" - | variety_sd " + factor_variety_sd);
                
                // Save modifiers
                
                PS.log(" $1Done!");
                running = false;
                whenDone.run();
            }
        });
        
        
        
        
        // sort plots by popularity
        
        // get the arrays for each modifier

        // get the rankings for each modifier
        
        /*
         * For each modifier:
         *  - get the arrays
         *  - get the rankings
         */
    }
    
    public static void logln(Object obj) {
        System.out.println(log(obj));
    }
    
    public static String log(Object obj) {
        String result = "";
        if (obj.getClass().isArray()) {
            String prefix = "";
            
            for(int i=0; i<Array.getLength(obj); i++){
                result += prefix + log(Array.get(obj, i));
                prefix = ",";
            }
            return "( " + result + " )";
        }
        else if (obj instanceof List<?>) {
            String prefix = "";
            for (Object element : (List<?>) obj) {
                result += prefix + log(element);
                prefix = ",";
            }
            return "[ " + result + " ]";
        }
        else {
            return obj.toString();
        }
    }
    
    /**
     * Get correllation coefficient
     * @return
     */
    public static double getCC(int n, int sum) {
        return 1 - (6 * (double) sum) / (n * (n*n - 1)); 
    }
    
    /**
     * Sum of an array
     * @param array
     * @return
     */
    public static int sum(int[] array) {
        int sum = 0;
        for (int value : array ) {
            sum += value;
        }
        return sum;
    }
    
    /**
     * A simple array squaring algorithm<br>
     *  - Used for calculating the variance
     * @param array
     * @return
     */
    public static int[] square(int[] array) {
        array = array.clone();
        for (int i = 0; i < array.length; i++) {
            array[i] *= array[i];
        }
        return array;
    }
    
    /**
     * An optimized lossy standard deviation algorithm
     * @param ranks
     * @return
     */
    public static int[] getSD(int[]...ranks) {
        if (ranks.length == 0) {
            return null;
        }
        int size = ranks[0].length;
        int arrays = ranks.length;
        int[] result = new int[size];
        for (int j = 0; j < size; j++) {
            int sum = 0;
            for (int i = 0; i < ranks.length; i++) {
                sum += ranks[i][j];
            }
            int mean = sum / arrays;
            int sd = 0;
            for (int i = 0; i < ranks.length; i++) {
                int value = ranks[i][j];
                sd += value < mean ? mean - value : value - mean;
            }
            result[j] = sd;
        }
        return result;
    }
    
    /**
     * An optimized algorithm for ranking a very specific set of inputs<br>
     *  - Input is an array of int with a max size of 102400
     *  - This allows for optimizations beyond any standard sorting function
     * @param input
     * @return
     */
    public static int[] rank(final int[] input) {
        int[] cache = new int[102400];
        int max = 0;
        if (input.length < 102400) {
            for (int value : input) {
                if (value > max) {
                    max = value;
                }
                cache[value]++;
            }
        }
        else {
           max = cache.length - 1;
           for (int value : input) {
               cache[value]++;
           }
        }
        int last = 0;
        for (int i = max; i >= 0; i--) {
            if (cache[i] != 0) {
                cache[i] += last;
                last = cache[i];
                if (last == input.length) {
                    break;
                }
            }
        }
        
        int[] ranks = new int[input.length];
        for (int i = 0; i < input.length; i++) {
            int index = input[i];
            ranks[i] = cache[index];
            cache[index]--;
        }
        return ranks;
    }
    
    public static void sort(int[] input) {
        final int SIZE = 10;
        List<Integer>[] bucket = new ArrayList[SIZE];
        for (int i = 0; i < bucket.length; i++) {
            bucket[i] = new ArrayList<Integer>();
        }
        boolean maxLength = false;
        int tmp = -1, placement = 1;
        while (!maxLength) {
            maxLength = true;
            for (Integer i : input) {
                tmp = i / placement;
                bucket[tmp % SIZE].add(i);
                if (maxLength && tmp > 0) {
                    maxLength = false;
                }
            }
            int a = 0;
            for (int b = 0; b < SIZE; b++) {
                for (Integer i : bucket[b]) {
                    input[a++] = i;
                }
                bucket[b].clear();
            }
            placement *= SIZE;
        }
    }
}
