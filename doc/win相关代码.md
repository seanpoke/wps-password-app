```csharp
        private bool WritePasswordToZipMetadata(string filePath, string password)
        {
            try
            {
                // 检查是否为有效的ZIP文件或Office文档
                if (!IsValidZipFile(filePath))
                {
                    Logger.Error($"文件不是有效的ZIP文件: {filePath}");
                    return false;
                }

                // 构建密码元数据块
                byte[] metadataBlock = BuildMetadataBlock(1, password); // Type 1 = Password

                // 尝试以不同的FileShare模式打开文件
                FileStream fs = null;
                try
                {
                    // 尝试以共享读取模式打开文件
                    fs = new FileStream(filePath, FileMode.Open, FileAccess.ReadWrite, FileShare.Read);
                }
                catch
                {
                    // 如果失败，尝试以独占模式打开
                    fs = new FileStream(filePath, FileMode.Open, FileAccess.ReadWrite, FileShare.None);
                }

                using (fs)
                {
                    // 尝试查找EOCD位置，但即使失败也继续执行
                    long eocdPosition = -1;
                    try
                    {
                        eocdPosition = FindEndOfCentralDirectory(filePath);
                    }
                    catch (Exception ex)
                    {
                        Logger.Warning($"查找EOCD时出错: {ex.Message}，将直接附加到文件末尾");
                    }

                    if (eocdPosition >= 0)
                    {
                        try
                        {
                            // 找到EOCD，读取EOCD的长度
                            fs.Seek(eocdPosition, SeekOrigin.Begin);
                            byte[] eocdBuffer = new byte[22];
                            fs.Read(eocdBuffer, 0, 22);
                            
                            // 读取注释长度（EOCD的第20-21字节）
                            ushort commentLength = BitConverter.ToUInt16(eocdBuffer, 20);
                            long eocdEndPosition = eocdPosition + 22 + commentLength;
                            
                            // 尝试查找旧的元数据
                            long existingMetadataStart = -1;
                            try
                            {
                                existingMetadataStart = FindMetadataStartPosition(filePath);
                            }
                            catch (Exception ex)
                            {
                                Logger.Warning($"查找元数据位置时出错: {ex.Message}");
                            }

                            if (existingMetadataStart > 0 && existingMetadataStart >= eocdEndPosition)
                            {
                                // 截断到EOCD结束位置
                                fs.SetLength(eocdEndPosition);
                                Logger.Debug($"移除旧的元数据，截断到EOCD结束位置: {eocdEndPosition}");
                            }
                            else
                            {
                                // 移动到文件末尾
                                fs.Seek(0, SeekOrigin.End);
                            }
                        }
                        catch (Exception ex)
                        {
                            Logger.Warning($"处理EOCD时出错: {ex.Message}，将直接附加到文件末尾");
                            // 移动到文件末尾
                            fs.Seek(0, SeekOrigin.End);
                        }
                    }
                    else
                    {
                        // 找不到EOCD，直接附加到文件末尾
                        fs.Seek(0, SeekOrigin.End);
                    }

                    // 写入元数据块
                    fs.Write(metadataBlock, 0, metadataBlock.Length);

                    Logger.Info($"密码已成功写入到 {filePath} 的ZIP尾部");
                    return true;
                }
            }
            catch (Exception ex)
            {
                Logger.Error($"写入ZIP元数据失败: {ex.Message}");
                return false;
            }
        }

        /// <summary>
        /// 查找元数据在文件中的起始位置
        /// </summary>
        private long FindMetadataStartPosition(string filePath)
        {
            try
            {
                // 尝试以不同的FileShare模式打开文件
                FileStream fs = null;
                try
                {
                    // 尝试以共享读取模式打开文件
                    fs = new FileStream(filePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
                }
                catch
                {
                    // 如果失败，尝试以共享读取模式打开
                    fs = new FileStream(filePath, FileMode.Open, FileAccess.Read, FileShare.Read);
                }

                using (fs)
                {
                    long fileLength = fs.Length;
                    if (fileLength < 15) // 最小元数据块长度
                    {
                        return -1;
                    }

                    // 从文件末尾开始搜索Magic
                    long searchStart = Math.Max(0, fileLength - 1024); // 最多搜索1KB
                    int searchLength = (int)(fileLength - searchStart);

                    byte[] buffer = new byte[searchLength];
                    fs.Seek(searchStart, SeekOrigin.Begin);
                    fs.Read(buffer, 0, searchLength);

                    byte[] magicBytes = Encoding.ASCII.GetBytes(METADATA_MAGIC);

                    // 从后向前搜索Magic
                    for (int i = searchLength - magicBytes.Length; i >= 0; i--)
                    {
                        bool found = true;
                        for (int j = 0; j < magicBytes.Length; j++)
                        {
                            if (buffer[i + j] != magicBytes[j])
                            {
                                found = false;
                                break;
                            }
                        }

                        if (found)
                        {
                            long metadataPosition = searchStart + i;
                            Logger.Debug($"找到元数据位置: {metadataPosition}");
                            return metadataPosition;
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                Logger.Error($"查找元数据位置时出错: {ex.Message}");
            }

            return -1;
        }
```

