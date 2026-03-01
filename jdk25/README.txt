将 JDK 25 作为项目一部分使用：

1. 把已下载的 JDK 25 解压到此目录（jdk25），保证目录结构为：
   jdk25/
   ├── bin/
   │   ├── javac.exe   (Windows)
   │   ├── javac       (Linux/Mac)
   │   └── ...
   ├── lib/
   └── ...

2. 即：解压后 jdk25/bin 下要有 javac（或 Windows 的 javac.exe），不要多一层目录。
   若压缩包内是 jdk-25.0.x 这种单层目录，把其内容（bin、lib 等）挪到 jdk25 下即可。

3. 此目录下的 JDK 文件已加入 .gitignore，不会提交；其他人克隆后自行把 JDK 25 放到此处即可编译。
