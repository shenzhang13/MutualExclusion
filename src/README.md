**Team Member**

Shen Zhang  sxz162330 

Qi Wang        qxw170003





1. Copy the code to csgrads1 server

   ```powershell
   scp Application.java Server.java Message.java Tester.java config.txt netid@csgrads1:/PROJECT_DIR
   ```

2. Update the file path of config.txt on **Application.java**, **launcher.sh** and **cleanup.sh**

3. Compile java file

   ```powershell
   javac *.java
   ```

4. Run launcher.sh

   ```powershell
   ./launcher.sh
   ```

5. To validate the correctness of executing the critical section

   ```powershell
   java Tester
   ```

6. After testing, update the configuration and run cleanup.sh to kill all the process

   ``` powershell
   ./cleanup.sh
   ```

   

