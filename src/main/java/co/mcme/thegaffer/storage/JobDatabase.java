/*  This file is part of TheGaffer.
 * 
 *  TheGaffer is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  TheGaffer is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with TheGaffer.  If not, see <http://www.gnu.org/licenses/>.
 */
package co.mcme.thegaffer.storage;

import co.mcme.thegaffer.TheGaffer;
import co.mcme.thegaffer.events.JobEndEvent;
import co.mcme.thegaffer.events.JobStartEvent;
import static co.mcme.thegaffer.storage.TSfetcher.InLobby;
import co.mcme.thegaffer.utilities.Util;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.Getter;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;

public class JobDatabase {

    @Getter
    private static final TreeMap<String, Job> activeJobs = new TreeMap();
    @Getter
    private static final TreeMap<String, Job> inactiveJobs = new TreeMap();

    public static int loadJobs() throws IOException {
        int count = 0;
        File activeJobFolder = new File(TheGaffer.getPluginDataFolder() + TheGaffer.getFileSeperator() + "jobs");
        if (!activeJobFolder.exists()) {
            activeJobFolder.mkdirs();
        }
        String[] aJ = activeJobFolder.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(TheGaffer.getFileExtension());
            }
        });
        ArrayList<Job> tjobs = new ArrayList();
        for (String fName : aJ) {
            File jFile = new File(activeJobFolder, fName);
            if (!jFile.isDirectory()) {
                Job job = TheGaffer.getJsonMapper().readValue(jFile, Job.class);
                tjobs.add(job);
            }
        }
        for (Job jerb : tjobs) {
            jerb.setDirty(false);
            if (jerb.isRunning()) {
                activateJob(jerb);
                TheGaffer.scheduleOwnerTimeout(jerb);
                count++;
            } else {
                inactiveJobs.put(jerb.getName(), jerb);
                count++;
            }
        }
        return count;
    }

    public static void saveJobs() {
        File jobFolder = new File(TheGaffer.getPluginDataFolder() + TheGaffer.getFileSeperator() + "jobs");
        if (!jobFolder.exists()) {
            jobFolder.mkdirs();
        }
        for (Job jerb : activeJobs.values()) {
            if (jerb.isDirty()) {
                boolean successful = true;
                File newLocation = new File(jobFolder, jerb.getName() + TheGaffer.getFileExtension() + ".new");
                File afterLocation = new File(jobFolder, jerb.getName() + TheGaffer.getFileExtension());
                try {
                    TheGaffer.getJsonMapper().writeValue(newLocation, jerb);
                } catch (IOException ex) {
                    Util.severe(Arrays.toString(ex.getStackTrace()));
                    successful = false;
                } finally {
                    if (successful) {
                        if (afterLocation.exists()) {
                            afterLocation.delete();
                        }
                        newLocation.renameTo(afterLocation);
                    }
                }
                jerb.setDirty(false);
            }
        }
        for (Job jerb : inactiveJobs.values()) {
            if (jerb.isDirty()) {
                boolean successful = true;
                File newLocation = new File(jobFolder, jerb.getName() + TheGaffer.getFileExtension() + ".new");
                File afterLocation = new File(jobFolder, jerb.getName() + TheGaffer.getFileExtension());
                try {
                    TheGaffer.getJsonMapper().writeValue(newLocation, jerb);
                } catch (IOException ex) {
                    Util.severe(Arrays.toString(ex.getStackTrace()));
                    successful = false;
                } finally {
                    if (successful) {
                        if (afterLocation.exists()) {
                            afterLocation.delete();
                        }
                        newLocation.renameTo(afterLocation);
                    }
                }
                jerb.setDirty(false);
            }
        }
    }

    public static boolean activateJob(Job j) {
        if (activeJobs.containsKey(j.getName())) {
            return false;
        }
        j.generateBounds();
        activeJobs.put(j.getName(), j);
        TheGaffer.getServerInstance().getPluginManager().registerEvents(j, TheGaffer.getPluginInstance());
        j.setDirty(true);
        saveJobs();
        TheGaffer.getServerInstance().getPluginManager().callEvent(new JobStartEvent(j));
        return true;
    }

    public static boolean deactivateJob(Job j) {
        if (!activeJobs.containsKey(j.getName())) {
            return false;
        }
        j.setRunning(false);
        j.setEndTime(System.currentTimeMillis());
        j.setDirty(true);
        activeJobs.remove(j.getName());
        inactiveJobs.put(j.getName(), j);
        HandlerList.unregisterAll(j);
        saveJobs();
        TheGaffer.getServerInstance().getPluginManager().callEvent(new JobEndEvent(j));
        return true;
    }

    public static Job getJobWorking(OfflinePlayer p) {
        for (Job job : activeJobs.values()) {
            if (job.isPlayerWorking(p) || job.isPlayerHelper(p) || p.getName().equals(job.getOwner())) {
                return job;
            }
        }
        return null;
    }
    
    public static void TSfetch(){
        TheGaffer.getPluginInstance().getLogger().info("tread running");
        String dbPath = System.getProperty("user.dir") + "/plugins/TheGaffer/LobbyDB";
//        Path dbDir = Paths.get(dbPath);
        if(!JobDatabase.getActiveJobs().isEmpty()){
            for(String JobName : JobDatabase.getActiveJobs().keySet()){
                Job job = JobDatabase.getActiveJobs().get(JobName);
                TheGaffer.getPluginInstance().getLogger().info(job.getTSchannel());
                if(!job.getTSchannel().equalsIgnoreCase("0")){
                    try {
                        String TSpath = job.getTSchannel().toLowerCase();
                        Scanner s;
                        s = new Scanner(new File(dbPath + "/" + TSpath + ".txt"));
                        while (s.hasNext()){
                            InLobby.add(s.next());
                        }
                        s.close();
                        job.setAdmitedWorkers(InLobby);
                    } catch (FileNotFoundException ex) {
                        Logger.getLogger(TSfetcher.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
             }
        }
    }
}
