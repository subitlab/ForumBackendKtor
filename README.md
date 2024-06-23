# ForumBackendKtor

新一代论坛后端，完全使用ktor和Kotlin/Jvm

开源协议: [AGPL-3.0](https://www.gnu.org/licenses/agpl-3.0.html)

# 使用说明
## 从源码构建
1. 确保您的电脑上安装了JDK 17或更高版本
2. 克隆本仓库
3. 运行`./gradlew clean build`构建项目
4. 在`build/libs`目录下找到`ForumBackendKtor-all.jar`文件
## 运行
请先确保您的电脑上拥有JRE 17或更高版本, 在命令行中输入以下内容以检查是否安装了JRE及其版本
```shell
java -version
```
### 初始化
首次启动服务前需要初始化, 在命令行中执行以下内容
```shell
java -jar <Jar> [-workDir=<工作目录>] [-config=<主配置文件路径>]
```
- `<Jar>`为构建出的jar文件, 见`从源码构建`部分
- `-workDir`为可选参数，指定工作目录，默认为当前目录
- `-config`为可选参数，指定主配置文件目录，默认为`<工作目录>/config.yaml`，在运行上面的命令前请确保该文***不存在***。

运行该命令后会收到日志
```shell
[2024-06-23 00:24:13][SEVERE] config.yaml not found, the default config has been created, please modify it and restart the program 
```
此时会自动创建工作目录并生成默认的主配置文件

打开工作目录，目录中应包含以下内容：
- `config.yaml`文件(若指定其他目录或名称则以指定的为准)
- `configs`文件夹，其中包含若干默认的配置文件
- `logs`文件夹

进行如下操作：
- 打开主配置文件(默认为`<工作目录>/config.yaml`)，根据提示编辑数据库配置等信息，然后保存文件 
- 打开`configs`文件夹，根据需要编辑默认的配置文件，然后保存文件
### 启动服务
在命令行中执行以下内容
```shell
java -jar <Jar> [-workDir=<工作目录>] [-config=<主配置文件路径>] [-port=<端口号>] [-host=<主机地址>] [-debug=<true/false>]
```
- `<Jar>`为构建出的jar文件, 见`从源码构建`部分
- `-workDir`为可选参数，指定工作目录，默认为当前目录
- `-config`为可选参数，指定主配置文件目录，默认为`<工作目录>/config.yaml`
- `-port`为可选参数，指定服务端口号，默认为`8080`
- `-host`为可选参数，指定主机地址，默认为`127.0.0.1`
- `-debug`为可选参数，指定是否启用debug模式，默认为`false`

当出现如下两行日志时即代表服务器已经启动(若日志等级大于INFO将不会打印)
```shell
[2024-06-23 01:22:59][INFO] Application started in 0.691 seconds.
[2024-06-23 01:23:01][INFO] Responding at http://127.0.0.1:8080
```
### 使用说明
- 服务启动后，可以通过浏览器访问`http://<主机地址>:<端口号>/api-docs/index.html`来查看API文档
- 在命令行中输入`help`可以查看所有可用命令
- 在命令行中输入`stop`可以停止服务